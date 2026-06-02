# Troubleshooting

Pickbit 백엔드를 구현하면서 가장 신경 쓴 부분은 단순 CRUD가 아니라 **경매 도메인에서 동시에 여러 사용자가 같은 자원을 변경하는 상황**이었다. 상품 등록 자체는 비교적 단순하지만, 경매가 시작되면 같은 경매에 여러 사용자가 동시에 입찰하고, 마감 시각에는 스케줄러가 낙찰자를 결정하며, 그 결과가 상품 상태와 실시간 화면에 반영되어야 한다.

처음에는 기능 단위로 구현을 시작했지만, 실제 흐름을 따라가면서 다음 문제가 반복해서 드러났다.

- 같은 경매에 동시에 입찰하면 최고 입찰이 꼬일 수 있다.
- Redis 락을 써도 DB 커밋보다 먼저 락을 풀면 안전하지 않다.
- WebSocket 알림을 트랜잭션 안에서 보내면 DB 반영 전 성공 알림이 나갈 수 있다.
- `auction-service`와 `product-service`는 서로 다른 DB를 쓰므로 상태 변경을 한 트랜잭션으로 묶을 수 없다.
- 경매 예정과 경매 진행 중은 사용자에게 다른 상태로 보여야 한다.

이 문서는 그 문제들을 어떤 방식으로 발견했고, 현재 코드에서는 어떻게 해결했는지 정리한 기록이다.

---

## 1. 경매 입찰 동시성

### 1.1 동시에 여러 사용자가 입찰하면 최고 입찰이 꼬일 수 있었다

경매에서 가장 먼저 생각해야 했던 문제는 동시 입찰이었다. 예를 들어 현재가가 `10,000원`이고 최소 입찰 단위가 `1,000원`일 때, 두 사용자가 거의 동시에 `15,000원`, `15,500원`을 입찰할 수 있다.

만약 두 요청이 같은 현재가 `10,000원`을 동시에 읽고 각각 검증을 통과하면, 둘 다 유효한 입찰처럼 저장될 수 있다. 그러면 `Auction.currentPrice`와 `BidStatus.ACTIVE`가 서로 맞지 않거나, 최고 입찰이 둘 이상 생길 수 있다.

처음에는 DB 트랜잭션만으로 충분할지 고민했지만, `auction-service`가 여러 인스턴스로 뜰 수 있고 경매 종료 스케줄러도 같은 경매를 수정하기 때문에 애플리케이션 레벨에서 경매 단위 직렬화가 필요하다고 판단했다.

그래서 같은 경매에 대한 입찰은 Redis 분산 락으로 줄 세우도록 했다.

```text
auction:bid:lock:{auctionId}
```

현재 `BidService`는 입찰 처리 전에 경매 ID 기반 락을 잡는다.

```java
RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
boolean acquired = lock.tryLock(5, TimeUnit.SECONDS);
```

이 구조에서는 같은 `auctionId`에 대한 입찰만 직렬화된다. 서로 다른 경매는 다른 락 키를 사용하므로 병렬 처리가 가능하다.

실제 처리 흐름은 다음과 같다.

```text
1. A, B가 같은 경매에 동시에 입찰
2. A가 auction:bid:lock:10 획득
3. B는 최대 5초 대기
4. A 입찰 검증/저장/currentPrice 갱신/DB commit
5. A가 lock 해제
6. B가 lock 획득
7. B는 A가 반영한 최신 currentPrice 기준으로 다시 검증
```

같은 금액을 동시에 입찰한 경우도 먼저 락을 얻은 요청만 성공한다. 뒤따르는 요청은 이미 갱신된 현재가 기준으로 최소 입찰 단위를 다시 만족해야 한다.

---

### 1.2 Redis 락을 잡아도 DB 커밋 전에 풀면 안전하지 않았다

Redis 락을 도입한 뒤에도 한 가지 미묘한 문제가 남아 있었다. 처음에는 `@Transactional` 메서드 안에서 락을 잡고, `finally`에서 락을 해제하는 형태였다.

```java
@Transactional
public BidResponse placeBid(...) {
    RLock lock = redissonClient.getLock(...);
    try {
        lock.tryLock(...);
        return bidProcessor.process(...);
    } finally {
        lock.unlock();
    }
}
```

이 코드는 겉보기에는 안전해 보인다. 하지만 Spring 트랜잭션은 메서드가 끝난 뒤 commit된다. 즉 `finally`의 `unlock()`이 실행된 뒤 실제 DB commit이 수행될 수 있다.

문제가 되는 순서는 다음과 같다.

```text
1. A가 Redis lock 획득
2. A가 DB 변경
3. finally에서 Redis lock 해제
4. B가 Redis lock 획득
5. A의 DB commit은 아직 완료 전
6. B가 오래된 currentPrice를 읽을 가능성
```

이 문제는 아주 짧은 타이밍 이슈지만, 경매에서는 치명적일 수 있다. 락은 “이전 입찰의 결과가 확정된 뒤” 다음 입찰을 들여보내야 의미가 있다.

그래서 `BidService.placeBid()`의 외부 `@Transactional`을 제거하고, 락을 잡은 상태에서 `TransactionTemplate`으로 트랜잭션을 명시적으로 실행하도록 바꿨다.

```java
public BidResponse placeBid(String bidderNickname, Long auctionId, BidCreateRequest request) {
    RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
    try {
        boolean acquired = lock.tryLock(5, TimeUnit.SECONDS);
        if (!acquired) {
            throw new InvalidAuctionStatusException("입찰 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }
        return transactionTemplate.execute(status ->
                bidProcessor.process(bidderNickname, auctionId, request)
        );
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new InvalidAuctionStatusException("입찰 처리 중 오류가 발생했습니다.");
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

이제 흐름은 다음처럼 정리된다.

```text
Redis lock 획득
-> DB 트랜잭션 시작
-> 입찰 검증/저장/currentPrice 갱신
-> DB commit
-> Redis lock 해제
```

락 해제 시점이 DB commit 이후로 밀렸기 때문에, 다음 입찰은 이전 입찰 결과가 DB에 확정된 뒤 처리된다.

---

### 1.3 고정 lease time은 긴 처리 중 락이 풀리는 문제가 있었다

처음에는 Redis 락을 다음처럼 잡았다.

```java
lock.tryLock(5, 10, TimeUnit.SECONDS);
```

이 코드는 직관적으로는 안전해 보였다. 서버가 락을 잡은 뒤 죽더라도 10초 뒤 락이 자동으로 풀리기 때문이다. 하지만 경매 입찰에서는 이 10초가 오히려 위험할 수 있었다.

입찰 처리 중 DB 커밋 지연, GC pause, Redis/network 지연, WebSocket 발행 지연이 겹치면 작업은 아직 끝나지 않았는데 Redis 락만 먼저 만료될 수 있다. 그러면 같은 경매에 대한 다음 입찰이 락을 얻고 들어와 동시에 같은 `Auction.currentPrice`를 수정할 가능성이 다시 열린다.

그래서 lease time을 직접 지정하지 않는 방식으로 변경했다.

```java
lock.tryLock(5, TimeUnit.SECONDS);
```

Redisson은 lease time을 직접 지정하지 않으면 watchdog을 통해 락 TTL을 자동 연장한다. 락을 보유한 스레드가 살아 있고 작업이 진행 중이면 TTL이 계속 연장되고, 서버가 죽으면 watchdog도 멈추기 때문에 일정 시간 뒤 락이 해제된다.

즉 현재 방식은 다음 균형을 의도한다.

```text
정상 처리 중: watchdog이 TTL 자동 연장
서버 장애 시: watchdog 중단 후 TTL 만료로 락 해제
```

운영에서는 락 점유 시간이 비정상적으로 길어지는지, Redis 연결 지연이 있는지 같이 모니터링해야 한다.

---

### 1.4 경매 종료 스케줄러와 입찰 API도 같은 락을 공유해야 했다

입찰만 직렬화해서는 충분하지 않았다. 경매 종료 시각이 되면 `AuctionScheduler`가 낙찰자를 결정하고 경매를 종료한다. 이때 입찰 API와 스케줄러가 같은 경매를 동시에 만질 수 있다.

예를 들어 마감 직전에 입찰 요청이 들어온 동시에 스케줄러가 만료 경매를 조회하면, 둘 중 무엇이 먼저 처리되느냐에 따라 낙찰자가 달라질 수 있다.

그래서 스케줄러도 입찰과 같은 락 키를 사용한다.

```java
RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
acquired = lock.tryLock(0, TimeUnit.SECONDS);
```

스케줄러는 락을 얻은 뒤 경매를 DB에서 다시 조회한다.

```java
Auction fresh = auctionRepository.findById(auctionId).orElse(null);
if (fresh == null || fresh.getAuctionStatus() != AuctionStatus.ACTIVE) {
    return false;
}
```

이 double-check가 필요한 이유는, 스케줄러가 만료 경매 목록을 읽은 뒤 락을 기다리는 사이 다른 요청이 이미 경매를 종료했을 수 있기 때문이다.

입찰 쪽에서도 경매 상태뿐 아니라 종료 시각을 다시 검증한다.

```java
if (auction.getAuctionStatus() != AuctionStatus.ACTIVE) {
    throw new InvalidAuctionStatusException("ACTIVE 상태의 경매에만 입찰할 수 있습니다.");
}
if (auction.getEndTime() != null && auction.getEndTime().isBefore(LocalDateTime.now())) {
    throw new InvalidAuctionStatusException("종료된 경매에는 입찰할 수 없습니다.");
}
```

이 구조에서는 마감 시점에 입찰과 종료가 충돌해도 한쪽만 먼저 처리된다.

```text
입찰이 먼저 lock 획득 -> 입찰 commit 후 스케줄러가 최신 입찰 기준으로 종료
스케줄러가 먼저 lock 획득 -> 경매 ENDED 후 입찰은 상태 검증에서 실패
```

종료 처리가 한 주기 늦어질 수 있지만, 중복 낙찰이나 잘못된 최고 입찰보다 훨씬 안전한 선택이라고 판단했다.

---

## 2. 실시간 알림과 트랜잭션

### 2.1 WebSocket을 트랜잭션 안에서 보내면 가짜 성공 알림이 나갈 수 있었다

입찰이 성공하면 같은 경매 방(`/topic/auctions/{auctionId}`)을 구독 중인 사용자들에게 새 현재가를 알려야 한다. 처음에는 입찰 처리 중 바로 WebSocket을 발행했다.

문제는 WebSocket 발행이 DB 트랜잭션과 원자적으로 묶이지 않는다는 점이다.

위험한 순서는 다음과 같다.

```text
1. Bid 저장
2. Auction.currentPrice 변경
3. WebSocket으로 성공 이벤트 발행
4. DB commit 실패
5. 클라이언트는 성공으로 봤지만 DB에는 반영되지 않음
```

이 문제를 피하려면 “DB commit이 성공한 뒤” 실시간 알림을 보내야 한다. 그래서 `BidProcessor`와 `AuctionScheduler`는 직접 `SimpMessagingTemplate.convertAndSend(...)`를 호출하지 않고, 애플리케이션 이벤트만 발행하도록 변경했다.

```java
eventPublisher.publishEvent(new AuctionRealtimeEvent(
        auctionId,
        AuctionBidEvent.ofNewBid(request.bidAmount(), bidderNickname, now)
));
```

실제 WebSocket 발행은 트랜잭션 커밋 이후 리스너에서 수행한다.

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void send(AuctionRealtimeEvent event) {
    messagingTemplate.convertAndSend("/topic/auctions/" + event.auctionId(), event.payload());
}
```

현재 흐름은 다음과 같다.

```text
입찰 검증/저장
-> AuctionRealtimeEvent 발행
-> DB commit
-> AFTER_COMMIT listener 실행
-> WebSocket 발행
```

이제 DB에 반영되지 않은 입찰이 실시간 화면에 먼저 노출되는 문제를 줄일 수 있다.

남은 한계도 있다. WebSocket 발행은 커밋 이후 side effect라서, 발행 실패 시 DB를 롤백할 수 없다. 지금은 실패 로그만 남긴다. 운영에서 실시간 알림 유실까지 복구하려면 별도 realtime outbox 또는 재시도 큐가 필요하다.

---

## 3. 서비스 간 정합성

### 3.1 auction-service와 product-service 상태를 동기 HTTP로 맞추면 이중 쓰기 문제가 생겼다

경매가 생성, 취소, 종료되면 상품 상태도 바뀌어야 한다. 예를 들어 경매가 생성되면 상품은 `AUCTION_SCHEDULED`, 경매가 시작되면 `IN_AUCTION`, 낙찰되면 `AUCTION_COMPLETED`가 되어야 한다.

처음에 단순하게 생각하면 `auction-service`에서 `product-service`의 내부 API를 동기 호출하면 될 것처럼 보인다.

```text
auction 상태 변경
-> product-service 상태 변경 HTTP 호출
```

하지만 두 서비스는 서로 다른 DB를 사용한다. `auction-service`의 트랜잭션이 성공했는데 `product-service` 호출이 실패하면 두 서비스 상태가 어긋난다. 반대로 product 호출은 성공했는데 auction 트랜잭션이 롤백되는 경우도 문제가 된다.

그래서 상품 상태 변경은 동기 HTTP가 아니라 Outbox/Kafka/Inbox 흐름으로 처리했다.

`auction-service`는 자기 로컬 트랜잭션 안에서 outbox 행만 저장한다.

```java
outboxRecorder.record(
        "Product",
        String.valueOf(productId),
        "product.status.update_requested",
        "UPDATE",
        Map.of("productId", productId, "status", status, "reason", reason, "auctionId", auctionId)
);
```

`OutBoxEvent`에는 Kafka로 전달할 메타데이터가 들어간다.

```java
private String eventId;
private String eventType;
private String action;
private String aggregateId;
private String payload;
```

Kafka 메시지 규격은 다음을 전제로 한다.

```text
topic: Product-topic
key: productId
header.action: UPDATE
header.event_id: eventId
payload: { productId, status, reason, auctionId }
```

`product-service`는 consumer에서 header와 key를 받는다.

```java
@Header("action") String action,
@Header("event_id") String eventId,
@Header(KafkaHeaders.RECEIVED_KEY) String aggregateId,
@Payload String messageBody
```

처리 순서는 다음과 같다.

```text
1. Inbox에서 eventId 중복 여부 확인
2. payload 역직렬화
3. Kafka key와 payload.productId 일치 검증
4. Product 상태 전이 검증
5. 상품 상태 변경
6. Inbox 성공 기록
```

이 구조는 즉시 일관성을 포기하는 대신, 이벤트 재처리와 중복 처리에 강해진다. 현재는 애플리케이션에 consumer와 Inbox가 구현되어 있고, outbox table을 Kafka topic으로 발행하는 Debezium/Kafka Connect 설정은 인프라 영역에서 연결해야 한다.

---

### 3.2 경매 예정과 경매 진행 중을 상품 상태에서 구분할 필요가 있었다

처음에는 상품 상태를 단순하게 유지하려고 `IN_AUCTION` 하나로 경매 예정과 진행 중을 모두 표현하려 했다. 하지만 실제 흐름을 보니 어색한 지점이 있었다.

경매를 생성하면 `AuctionStatus`는 `SCHEDULED`다. 아직 시작 시간이 되지 않았으므로 경매는 예정 상태다. 그런데 상품 상태를 바로 `IN_AUCTION`으로 바꾸면 상품 상세나 목록에서는 이미 “경매 중”처럼 보인다.

경매 플랫폼에서는 “경매 예정”과 “경매 진행 중”이 사용자 경험상 다르다. 그래서 상품 상태도 이를 분리했다.

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
상품 생성
Product: ACTIVE

경매 생성
Auction: SCHEDULED
Product: AUCTION_SCHEDULED 이벤트

경매 시작 시간 도달
Auction: ACTIVE
Product: IN_AUCTION 이벤트

경매 취소
Auction: CANCELLED
Product: ACTIVE 이벤트

경매 종료 - 낙찰/즉시구매
Auction: ENDED
Product: AUCTION_COMPLETED 이벤트

경매 종료 - 유찰
Auction: ENDED
Product: ACTIVE 이벤트
```

`AuctionService.createAuction()`은 경매 생성 후 `AUCTION_SCHEDULED` 이벤트를 기록한다.

```java
recordProductStatusUpdate(saved.getProductId(), "AUCTION_SCHEDULED", "AUCTION_CREATED", saved.getId());
```

`AuctionScheduler.activateScheduledAuctions()`는 시작 시간이 된 경매를 `ACTIVE`로 바꾸면서 `IN_AUCTION` 이벤트를 기록한다.

```java
toActivate.forEach(auction -> {
    auction.activate();
    recordProductStatusUpdate(auction.getProductId(), "IN_AUCTION", "AUCTION_STARTED", auction.getId());
});
```

이벤트 기반이라 경매 생성 직후 product DB에는 잠깐 `ACTIVE`가 남을 수 있다. 그래서 중복 경매 방지는 상품 상태만 믿지 않고 `auction-service` 자체 DB에서 `SCHEDULED` 또는 `ACTIVE` 경매가 있는지 추가로 확인한다.

---

## 4. 서비스 호출 안정성

### 4.1 경매 생성 시 product-service 장애가 auction-service로 전파될 수 있었다

경매를 생성하려면 대상 상품이 존재하는지, 상품 상태가 `ACTIVE`인지, 요청자가 판매자인지 확인해야 한다. 이 검증은 `product-service` 데이터가 필요하므로 `auction-service`에서 동기 조회가 발생한다.

현재는 외부 상세 조회 API가 아니라 내부 조회 API를 사용한다.

```java
.uri("/internal/products/{id}", productId)
```

이렇게 한 이유는 외부 상품 상세 조회 API가 조회수를 증가시키기 때문이다. 경매 생성 검증 때문에 상품 조회수가 올라가는 것은 부자연스럽다.

동기 호출 자체는 남아 있기 때문에 장애 격리도 필요했다. 그래서 `RestClientConfig`에서 timeout을 설정했다.

```java
factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
```

그리고 `ProductServiceClient`에는 CircuitBreaker와 Bulkhead를 적용했다.

```java
@CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
@Bulkhead(name = "productService", type = Bulkhead.Type.SEMAPHORE)
public ProductResponse getProduct(Long productId) { ... }
```

현재 적용은 `timeout + CircuitBreaker + Bulkhead`다. `TimeLimiter`는 적용하지 않았다. 동기 `RestClient` 호출에서는 우선 HTTP timeout으로 대기 시간을 제한하고, 장애가 누적되면 circuit breaker가 빠르게 차단하도록 했다.

---

## 5. 조회 성능

### 5.1 상품 검색에서 N+1이 발생할 수 있었다

상품 목록 검색은 `Product`, `Category`, `ProductImage`를 함께 보여준다. 엔티티를 그대로 조회한 뒤 mapper에서 `category.name`이나 `images`에 접근하면 LAZY 로딩 때문에 N+1이 발생할 수 있다.

목록 API는 화면에 필요한 필드가 비교적 명확하므로, 엔티티를 전부 가져오는 대신 QueryDSL DTO projection으로 바로 응답 DTO를 만들도록 했다.

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

이 방식은 목록 조회에서 필요한 컬럼만 가져오므로 N+1을 피할 수 있다. 대신 목록 화면 요구사항이 바뀌면 projection 쿼리도 함께 바꿔야 한다. 화면과 쿼리가 결합되는 트레이드오프는 있지만, 검색 API에는 이 방식이 더 적합하다고 판단했다.

---

## 6. 테스트와 운영 보조 이슈

### 6.1 Kafka listener에 `@Profile`을 걸지 않으면서 테스트가 실제 브로커에 붙을 수 있었다

Kafka consumer를 만들 때 `@Profile("deploy")` 같은 제한을 둘 수도 있었다. 하지만 현재 프로젝트에서는 환경별로 listener 클래스를 꺼버리기보다, 같은 코드가 모든 환경에서 뜨고 설정으로 제어되는 쪽을 선택했다.

그래서 `ProductStatusEventListener`와 `KafkaConfig`에는 `@Profile`을 걸지 않았다.

이 선택의 부작용도 있다. 테스트 실행 중 Kafka listener가 실제 broker 설정을 보고 consumer group join/leave 로그를 남길 수 있다.

현재 `KafkaConfig`는 기본 bootstrap server를 가진다.

```java
@Value("${spring.kafka.bootstrap-servers:localhost:9092}")
private String bootstrapServers;
```

향후 테스트 안정성을 더 높이려면 다음 중 하나를 고려할 수 있다.

- 테스트 profile에서 `spring.kafka.listener.auto-startup=false`
- Embedded Kafka 사용
- 테스트 전용 bootstrap server 주입

단, 운영 코드에 profile 제한을 걸지는 않는 방향을 유지한다.

---

### 6.2 Testcontainers 동적 포트 문제

`auction-service` 테스트는 MySQL과 Redis를 Testcontainers로 띄운다. 컨테이너는 내부 포트가 같아도 호스트 포트가 매번 달라질 수 있으므로, yml에 고정 포트를 적어두면 테스트가 로컬 환경에 새거나 connection refused가 날 수 있다.

현재는 Spring Boot의 `@ServiceConnection`을 사용해 컨테이너 정보를 Spring 설정에 자동 연결한다.

```java
@Bean
@ServiceConnection
MySQLContainer mysqlContainer() { ... }

@Bean
@ServiceConnection(name = "redis")
GenericContainer<?> redisContainer() { ... }
```

이 방식 덕분에 Redis 락 테스트와 MySQL 통합 테스트가 로컬 설치 상태에 덜 의존한다.

---

## 정리

| 분류 | 문제 | 현재 적용 |
|---|---|---|
| 입찰 동시성 | 같은 경매에 동시 입찰 시 최고가 꼬임 | `auction:bid:lock:{auctionId}` Redis 분산 락 |
| 트랜잭션 경계 | DB commit 전 lock 해제 가능성 | `TransactionTemplate`으로 commit 후 unlock |
| 락 TTL | 고정 lease time 만료 위험 | Redisson watchdog 방식 |
| 경매 종료 race | 스케줄러와 입찰 API 충돌 | 동일 Redis 락 + double-check |
| 실시간 알림 | commit 전 WebSocket 성공 알림 위험 | `@TransactionalEventListener(AFTER_COMMIT)` |
| 서비스 정합성 | auction/product 이중 쓰기 | Outbox + Kafka + Inbox |
| 상품 상태 | 예정/진행 구분 불가 | `AUCTION_SCHEDULED` / `IN_AUCTION` 분리 |
| 외부 호출 장애 | product-service 장애 전파 | timeout + CircuitBreaker + Bulkhead |
| 조회 성능 | 상품 검색 N+1 | QueryDSL DTO projection |
| 테스트 인프라 | Redis/MySQL 동적 포트 | `@ServiceConnection` |

---

## 남은 개선 과제

현재 구조는 MVP 수준의 경매 동시성에는 충분하지만, 운영 수준으로 올리려면 다음 항목을 추가로 검토해야 한다.

1. **WebSocket 재전송 보장**
   - 현재는 DB commit 이후 WebSocket 발행 실패 시 로그만 남긴다.
   - 실시간 알림 유실까지 복구하려면 realtime outbox 또는 재시도 큐가 필요하다.

2. **Kafka DLQ/retry 정책**
   - `product-service` consumer는 `Inbox`로 중복 처리를 하지만, 실패 이벤트의 재처리/DLQ 정책은 아직 명확하지 않다.

3. **동시성 부하 테스트**
   - 같은 경매에 10명 이상 동시 입찰
   - 즉시구매와 일반 입찰 동시 요청
   - 스케줄러 종료와 입찰 동시 요청
   - Redis 장애/지연 상황

4. **Kafka 테스트 격리**
   - `@Profile` 제한 없이 listener를 띄우는 방향은 유지하되, 테스트에서는 `auto-startup=false` 또는 Embedded Kafka를 검토한다.

5. **입찰 UX 백프레셔**
   - 현재는 5초 안에 락을 얻지 못하면 실패 응답을 준다.
   - 트래픽이 커지면 큐잉 후 “입찰 접수됨” UX를 제공하는 방식도 검토할 수 있다.

---

## 검증 명령

```bash
./gradlew :auction-service:test
./gradlew :product-service:test
```
