# Troubleshooting

`pickbit-backend`는 Gradle 멀티모듈 모노레포이지만 도메인 경계는 MSA를 지향한다. 현재 주요 흐름은 `product-service`와 `auction-service`가 분리된 DB를 사용하고, 경매 상태 변화는 Outbox/Kafka/Inbox 흐름으로 상품 상태에 최종 반영하는 구조다.

각 항목은 **현상 -> 원인 -> 현재 적용 -> 남은 리스크/트레이드오프** 순서로 정리한다.

---

## 1. 경매 도메인

### 1.1 경매 종료 시점 Race Condition

#### 현상
- 경매 종료 시각 직전/직후 입찰이 들어오면 낙찰자 산정이 흔들릴 수 있다.
- 스케줄러가 경매를 종료하는 순간 입찰 API가 같은 경매를 갱신할 수 있다.

#### 원인
입찰과 경매 종료 스케줄러가 같은 `Auction`과 `Bid` 데이터를 갱신한다. 둘 중 하나만 락을 사용하면 TOCTOU 문제가 생긴다.

#### 현재 적용
입찰과 스케줄러 종료 처리가 같은 Redis 락 키를 사용한다.

```text
auction:bid:lock:{auctionId}
```

`BidService`는 입찰 처리 전 락을 획득한다.

```java
RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
```

`AuctionScheduler`도 만료 경매 종료 시 같은 락을 획득하고, 락 획득 후 DB에서 경매를 다시 조회한다.

```java
Auction fresh = auctionRepository.findById(auctionId).orElse(null);
if (fresh == null || fresh.getAuctionStatus() != AuctionStatus.ACTIVE) {
    return false;
}
```

`BidProcessor`는 경매 상태뿐 아니라 종료 시각도 다시 검증한다.

```java
if (auction.getAuctionStatus() != AuctionStatus.ACTIVE) {
    throw new InvalidAuctionStatusException("ACTIVE 상태의 경매에만 입찰할 수 있습니다.");
}
if (auction.getEndTime() != null && auction.getEndTime().isBefore(LocalDateTime.now())) {
    throw new InvalidAuctionStatusException("종료된 경매에는 입찰할 수 없습니다.");
}
```

#### 남은 리스크/트레이드오프
- 입찰이 폭주하면 스케줄러 종료 처리가 다음 주기로 밀릴 수 있다.
- 그래도 중복 낙찰보다 종료 처리가 1~2초 지연되는 편이 안전하다.

---

### 1.2 Redis 락 해제와 DB 커밋 순서

#### 현상
동시 입찰에서 다음 요청이 락을 얻었는데 직전 입찰의 DB 커밋이 아직 완료되지 않았다면, 최신 `currentPrice`를 못 보고 검증할 수 있다.

#### 원인
`@Transactional` 메서드 안에서 `finally`로 Redis 락을 해제하면, 메서드 종료 후 트랜잭션 커밋이 수행될 수 있다. 이 경우 아주 짧게 아래 순서가 가능하다.

```text
Redis lock 해제
다음 입찰 lock 획득
직전 입찰 DB commit 완료 전
```

#### 현재 적용
`BidService.placeBid()`의 외부 `@Transactional`을 제거하고, 락을 잡은 뒤 `TransactionTemplate` 내부에서 입찰 처리를 실행한다. `transactionTemplate.execute(...)`가 반환된 뒤 락을 해제하므로 DB 커밋 이후에 락이 풀린다.

```java
public BidResponse placeBid(String bidderNickname, Long auctionId, BidCreateRequest request) {
    RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
    try {
        boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
        if (!acquired) {
            throw new InvalidAuctionStatusException("입찰 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }
        return transactionTemplate.execute(status ->
                bidProcessor.process(bidderNickname, auctionId, request)
        );
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

#### 남은 리스크/트레이드오프
- 트랜잭션 경계가 명시적으로 드러나 코드는 약간 더 직접적이다.
- 현재 `BidProcessor` 안에서 WebSocket 발행도 함께 수행한다. 더 엄밀히 하려면 WebSocket 발행은 `AFTER_COMMIT` 이벤트로 분리할 수 있다.

---

### 1.3 이중 쓰기와 서비스 간 데이터 정합성

#### 현상
- 경매는 종료됐는데 상품 상태는 여전히 판매 가능 상태로 남을 수 있다.
- 경매 생성/취소/종료와 상품 상태 변경을 동기 HTTP로 묶으면 한쪽만 성공하는 이중 쓰기 문제가 생긴다.

#### 원인
`auction-service`와 `product-service`는 서로 다른 DB를 사용한다. 한 서비스의 로컬 트랜잭션으로 두 DB를 원자적으로 변경할 수 없다.

#### 현재 적용
동기 상태 변경 호출 대신 Outbox/Kafka/Inbox 구조를 사용한다.

`auction-service`는 자기 트랜잭션 안에서 `OutBoxEvent`만 저장한다.

```java
outboxRecorder.record(
        "Product",
        String.valueOf(productId),
        "product.status.update_requested",
        "UPDATE",
        Map.of("productId", productId, "status", status, "reason", reason, "auctionId", auctionId)
);
```

`OutBoxEvent`는 Kafka header로 보낼 메타데이터를 가진다.

```java
private String eventId;
private String eventType;
private String action;
private String aggregateId;
private String payload;
```

`product-service`는 Kafka consumer에서 header와 key를 받는다.

```java
@Header("action") String action,
@Header("event_id") String eventId,
@Header(KafkaHeaders.RECEIVED_KEY) String aggregateId,
@Payload String messageBody
```

`ProductStatusEventHandler`는 다음 순서로 처리한다.

```text
1. Inbox에서 eventId 중복 확인
2. payload 역직렬화
3. Kafka key와 payload.productId 일치 검증
4. Product 상태 전이 검증
5. 상태 변경
6. Inbox 성공 기록
```

#### 남은 리스크/트레이드오프
- 즉시 일관성이 아니라 최종 일관성이다.
- Debezium/Kafka Connect 설정은 인프라 영역에서 outbox table -> Kafka topic으로 연결해야 한다.
- `Inbox.eventId` 중복 처리는 애플리케이션에 구현되어 있지만, 운영에서는 DLQ/재시도 정책도 필요하다.

---

### 1.4 경매 예정/진행 상품 상태 분리

#### 현상
경매가 `SCHEDULED`인데 상품 상태를 바로 `IN_AUCTION`으로 바꾸면, 상품 상태만 보고는 경매 예정인지 진행 중인지 구분할 수 없다.

#### 원인
경매 상태와 상품 상태의 책임이 다르다.

- `AuctionStatus.SCHEDULED`: 경매 예정
- `AuctionStatus.ACTIVE`: 경매 진행 중
- `ProductStatus`: 상품이 판매/경매/완료/삭제 중 어디에 있는지 표현

#### 현재 적용
상품 상태를 예정/진행으로 분리했다.

```java
ACTIVE("판매 중"),
AUCTION_SCHEDULED("경매 예정"),
IN_AUCTION("경매 중"),
INACTIVE("비활성화"),
AUCTION_COMPLETED("경매 종료"),
DELETED("삭제됨")
```

현재 상태 흐름은 다음과 같다.

```text
상품 생성: Product ACTIVE
경매 생성: Auction SCHEDULED, Product AUCTION_SCHEDULED 이벤트
경매 시작: Auction ACTIVE, Product IN_AUCTION 이벤트
경매 취소: Auction CANCELLED, Product ACTIVE 이벤트
경매 종료 낙찰/즉시구매: Auction ENDED, Product AUCTION_COMPLETED 이벤트
경매 종료 유찰: Auction ENDED, Product ACTIVE 이벤트
```

`AuctionService.createAuction()`은 경매 생성 후 상품 상태를 `AUCTION_SCHEDULED`로 요청한다.

```java
recordProductStatusUpdate(saved.getProductId(), "AUCTION_SCHEDULED", "AUCTION_CREATED", saved.getId());
```

`AuctionScheduler.activateScheduledAuctions()`는 시작 시간이 된 경매를 `ACTIVE`로 바꾸고 상품 상태 `IN_AUCTION` 이벤트를 기록한다.

```java
toActivate.forEach(auction -> {
    auction.activate();
    recordProductStatusUpdate(auction.getProductId(), "IN_AUCTION", "AUCTION_STARTED", auction.getId());
});
```

#### 남은 리스크/트레이드오프
- 상태 변경은 이벤트 기반이라 경매 생성 직후 상품 DB에는 잠깐 `ACTIVE`가 남을 수 있다.
- 그래서 중복 경매 방지는 상품 상태만 믿지 않고 `auction-service`의 `existsByProductIdAndAuctionStatusIn(SCHEDULED, ACTIVE)` 검사를 유지한다.

---

### 1.5 멀티 인스턴스에서 스케줄러 중복 실행

#### 현상
`auction-service`를 여러 대 띄우면 각 인스턴스의 `@Scheduled`가 동시에 실행될 수 있다.

#### 현재 적용
ShedLock과 Redis LockProvider를 사용한다.

```java
@SchedulerLock(name = "processAuctions", lockAtMostFor = "PT30S", lockAtLeastFor = "PT5S")
@Transactional
public void processAuctions() {
    LocalDateTime now = LocalDateTime.now();
    activateScheduledAuctions(now);
    closeExpiredAuctions(now);
}
```

#### 남은 리스크/트레이드오프
- `lockAtMostFor`는 가장 긴 스케줄러 작업 시간보다 충분히 길어야 한다.
- Redis 장애 시 스케줄러 락도 영향을 받는다.

---

### 1.6 락 대기 UX와 백프레셔

#### 현상
마감 직전 인기 경매에서 다수 요청이 동시에 들어오면 일부 사용자가 락 획득 실패 메시지를 받는다.

```text
입찰 처리 중입니다. 잠시 후 다시 시도해주세요.
```

#### 현재 적용
동일 경매 입찰은 Redis 락으로 직렬화한다.

```java
lock.tryLock(5, 10, TimeUnit.SECONDS)
```

#### 남은 리스크/트레이드오프
- 현재는 큐잉이 아니라 대기 후 실패 방식이다.
- 트래픽이 더 커지면 입찰 요청을 큐에 넣고 “접수됨” UX를 제공하는 방식도 검토할 수 있다.
- 클라이언트 자동 재시도는 지수 백오프와 jitter가 필요하다.

---

## 2. 성능 / 안정성

### 2.1 상품 검색 N+1

#### 현상
상품 목록 검색에서 엔티티를 조회한 뒤 mapper가 `category.name`, `images`에 접근하면 N+1이 발생할 수 있다.

#### 현재 적용
`ProductQueryRepository.searchSummary()`에서 DTO projection을 사용한다.

```java
queryFactory
        .select(Projections.constructor(ProductSummaryResponse.class,
                prod.id,
                prod.name,
                prod.startingPrice,
                prod.productStatus,
                prod.productCondition,
                prod.sellerNickname,
                cat.name,
                JPAExpressions
                        .select(img.imageUrl)
                        .from(img)
                        .where(img.product.eq(prod)
                                .and(img.imageType.eq(ImageType.THUMBNAIL)))
                        .orderBy(img.sortOrder.asc())
                        .limit(1),
                prod.createdDate
        ))
        .from(prod)
        .leftJoin(prod.category, cat)
```

#### 남은 리스크/트레이드오프
- DTO projection은 목록 화면 요구사항과 쿼리가 결합된다.
- 상세 화면처럼 엔티티 그래프가 필요한 API는 별도 fetch 전략이 필요할 수 있다.

---

### 2.2 product-service 동기 호출 장애 격리

#### 현상
경매 생성은 상품 검증을 위해 `product-service`를 동기 호출한다. 상품 서비스 지연이 경매 서비스 장애로 전파될 수 있다.

#### 현재 적용
`ProductServiceClient`는 내부 상품 조회 API를 호출한다.

```java
.uri("/internal/products/{id}", productId)
```

`RestClientConfig`에서 connect/read timeout을 설정한다.

```java
factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
```

Resilience4j CircuitBreaker와 Bulkhead를 적용했다.

```java
@CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
@Bulkhead(name = "productService", type = Bulkhead.Type.SEMAPHORE)
public ProductResponse getProduct(Long productId) { ... }
```

#### 남은 리스크/트레이드오프
- 현재 `TimeLimiter`는 적용되어 있지 않다. 동기 `RestClient` timeout으로 1차 방어한다.
- fallback은 상품 없음과 외부 장애를 구분해야 한다. 현재 4xx는 `AuctionProductNotFoundException`, 그 외 차단은 `ExternalServiceUnavailableException`으로 처리한다.

---

### 2.3 화면 단위 응답시간 누적

#### 현상
경매 상세 화면에서 경매, 상품, 판매자, 이미지 정보를 각각 호출하면 응답 시간이 누적된다.

#### 현재 적용
경매 생성 시점에 일부 상품 정보를 스냅샷으로 저장한다.

```java
productName
productThumbnailUrl
sellerNickname
```

#### 남은 리스크/트레이드오프
- 화면 요구사항이 커지면 BFF 또는 read model이 필요하다.
- 비정규화된 스냅샷은 정합성 부담이 생긴다.

---

### 2.4 Testcontainers Redis/MySQL 설정 매핑

#### 현상
테스트에서 컨테이너 포트가 동적으로 할당되면 정적 yml 설정과 충돌할 수 있다.

#### 현재 적용
`@ServiceConnection`으로 MySQL과 Redis 컨테이너를 Spring 설정에 자동 연결한다.

```java
@Bean
@ServiceConnection
MySQLContainer mysqlContainer() { ... }

@Bean
@ServiceConnection(name = "redis")
GenericContainer<?> redisContainer() { ... }
```

#### 남은 리스크/트레이드오프
- 컨테이너 재사용을 켜면 테스트 속도는 빨라지지만 데이터 잔존을 조심해야 한다.

---

### 2.5 Kafka consumer가 테스트/로컬에서 실제 브로커에 붙는 문제

#### 현상
`product-service:test` 실행 중 Kafka consumer가 실제 브로커에 join/leave group 로그를 남길 수 있다.

#### 원인
Kafka listener에 `@Profile` 제한을 두지 않기로 했고, `KafkaConfig`는 기본 bootstrap server를 사용한다.

```java
@Value("${spring.kafka.bootstrap-servers:localhost:9092}")
private String bootstrapServers;
```

#### 현재 적용
사용자 요구에 따라 Kafka 설정과 listener에는 `@Profile`을 걸지 않는다.

#### 남은 리스크/트레이드오프
- 테스트가 외부 Kafka 상태에 영향을 받을 수 있다.
- 후속 개선으로 `spring.kafka.listener.auto-startup=false` 테스트 설정, Embedded Kafka, 또는 테스트 전용 property 주입을 고려할 수 있다.

---

## 3. 운영 부수 이슈

### 3.1 파일 업로드 OOM과 객체 공개 범위

#### 현상
대용량 이미지를 `readAllBytes()`로 메모리에 모두 올리면 OOM 위험이 있다. 모든 객체를 public-read로 올리면 URL 노출만으로 접근 가능하다.

#### 현재 적용
`file-service`는 스트리밍 업로드와 `Visibility` 분기를 사용한다.

```java
RequestBody.fromInputStream(inputStream, contentLength)
```

공개/비공개 객체 접근 정책도 분리되어 있다.

```java
Visibility.PUBLIC
Visibility.PRIVATE
```

#### 남은 리스크/트레이드오프
- private 객체는 presigned URL TTL을 짧게 유지해야 한다.
- 파일 타입/확장자 검증과 악성 파일 검사는 별도 보강이 필요하다.

---

### 3.2 OpenAPI 문서 export 시점

#### 현상
런타임에서 자기 자신 `/v3/api-docs`를 호출해 문서를 export하면 부팅 시간과 운영 안정성에 영향을 줄 수 있다.

#### 현재 적용 방향
운영에서는 문서 export를 꺼두고, 개발 환경에서만 export하는 방식이 안전하다. 현재 각 서비스 yml은 `openapi.docs-export.enabled`를 환경별로 제어한다.

#### 남은 리스크/트레이드오프
- 운영 부팅 과정에서 파일 쓰기나 자기 자신 HTTP 호출은 피하는 것이 좋다.
- 가장 안전한 방식은 CI에서 문서를 생성하는 빌드타임 export다.

---

## 정리

| 분류 | 항목 | 상태 | 핵심 처방 |
|---|---|---|---|
| 1.1 | 경매 종료 race | 적용 | 입찰/종료 동일 Redis 락 + double-check + endTime 검증 |
| 1.2 | 락 해제와 DB 커밋 순서 | 적용 | Redis 락 내부에서 `TransactionTemplate` 실행, 커밋 후 unlock |
| 1.3 | 이중 쓰기 | 적용 | auction outbox + product Kafka consumer + Inbox idempotency |
| 1.4 | 상품 경매 예정/진행 분리 | 적용 | `AUCTION_SCHEDULED` / `IN_AUCTION` 분리 |
| 1.5 | 스케줄러 중복 | 적용 | ShedLock + Redis LockProvider |
| 1.6 | 락 대기 UX | 미적용 | 큐잉/백프레셔는 트래픽 증가 시 검토 |
| 2.1 | 상품 검색 N+1 | 적용 | QueryDSL DTO projection |
| 2.2 | 동기 호출 장애 격리 | 적용 | timeout + CircuitBreaker + Bulkhead |
| 2.3 | 응답시간 누적 | 일부 적용 | 경매 상품 스냅샷 저장, BFF/read model은 추후 |
| 2.4 | Testcontainers 매핑 | 적용 | `@ServiceConnection` |
| 2.5 | Kafka 테스트 브로커 연결 | 주의 | `@Profile` 미사용, 테스트 설정 보강 필요 |
| 3.1 | 파일 업로드 OOM/ACL | 적용 | streaming upload + Visibility |
| 3.2 | OpenAPI exporter | 주의 | 운영 export 비활성, 빌드타임 export 권장 |

---

## 실제 적용 이력

- `auction-service/src/main/java/com/pickbit/auctionservice/application/BidService.java`
  - Redis 락 획득 후 `TransactionTemplate`으로 입찰 처리.
  - DB 커밋 완료 후 락 해제.
- `auction-service/src/main/java/com/pickbit/auctionservice/application/BidProcessor.java`
  - 경매 상태, 종료 시각, 판매자 자기 입찰, 최소 입찰가 검증.
  - 성공 입찰 WebSocket 발행.
  - 즉시구매 완료 시 product 상태 변경 outbox 기록.
- `auction-service/src/main/java/com/pickbit/auctionservice/application/AuctionScheduler.java`
  - `SCHEDULED -> ACTIVE` 시작 처리.
  - 경매 시작 시 `IN_AUCTION` outbox 기록.
  - 경매 종료 시 동일 입찰 락 사용.
- `auction-service/src/main/java/com/pickbit/auctionservice/application/AuctionService.java`
  - 경매 생성 시 `AUCTION_SCHEDULED` outbox 기록.
  - 경매 취소 시 `ACTIVE` outbox 기록.
- `auction-service/src/main/java/com/pickbit/auctionservice/domain/OutBoxEvent.java`
  - `eventId`, `eventType`, `action`, `aggregateId`, `payload` 저장.
- `product-service/src/main/java/com/pickbit/productservice/infrastructure/kafka/ProductStatusEventListener.java`
  - Kafka header `action`, `event_id`, key(`RECEIVED_KEY`) 수신.
- `product-service/src/main/java/com/pickbit/productservice/application/event/ProductStatusEventHandler.java`
  - eventId 중복 확인, key/productId 검증, 상태 전이 처리, Inbox 기록.
- `product-service/src/main/java/com/pickbit/productservice/domain/Product.java`
  - `scheduleAuction()`, `startAuction()`, `releaseFromAuction()`, `completeAuction()` 상태 전이 메서드.
- `product-service/src/main/java/com/pickbit/productservice/domain/Inbox.java`
  - Kafka consumer idempotency 처리 이력 저장.

## 검증 명령

```bash
./gradlew :auction-service:test
./gradlew :product-service:test
```
