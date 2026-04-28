# Troubleshooting

`pickbit-backend`는 모노레포 기반 멀티모듈 구조이지만 도메인 경계는 MSA를 지향한다. 서비스마다 DB가 분리되어 있고, 입찰은 Redisson 분산 락으로 동시성을 제어하며, 경매 종료는 `@Scheduled` 배치로 처리한다. 이런 구조에서 실제로 마주칠 수 있는 트러블슈팅 사례를 정리한다.

각 항목은 **현상 → 원인 → Before → After → 트레이드오프** 순으로 기술한다.

---

## 1. 경매 도메인 (분산 시스템)

### 1.1 경매 종료 시점의 Race Condition (TOCTOU)

#### 현상
경매 종료 시각(예: 15:00:00) 직전·직후에 들어온 입찰이 간헐적으로 다음 중 하나의 이상 상태를 만든다.
- 종료된 경매에 입찰이 추가됨 (스케줄러가 결정한 낙찰자 이후의 입찰)
- 입찰이 정상 처리됐지만 화면에는 "이미 종료된 경매" 로 보임
- 낙찰자 산정 결과가 매번 다름

#### 원인
`BidService`와 `AuctionScheduler`가 같은 경매 행을 서로 다른 동기화 메커니즘으로 만진다.

- `BidService`는 Redisson 분산 락(`auction:bid:lock:{auctionId}`)으로 입찰을 직렬화한다.
- `AuctionScheduler.processAuctions()`는 분산 락을 잡지 않고 곧장 경매 상태를 CLOSED로 바꾼다.
- 즉, 입찰자가 락을 점유한 동안 스케줄러가 같은 경매를 종료시키거나, 반대로 스케줄러가 처리 중인 경매에 입찰이 들어가는 윈도우가 존재한다.

또 다른 문제는 `BidProcessor.process()`에서 락 획득 후 `auction.getAuctionStatus() != ACTIVE` 만 체크할 뿐, 종료 시각(`endTime`)은 검증하지 않는다는 점이다. 스케줄러 주기가 1초라도 그 사이의 입찰은 "ACTIVE 인데 endTime은 이미 지난" 상태에서 처리된다.

#### Before
`auction-service/src/main/java/com/pickbit/auctionservice/application/AuctionScheduler.java`
```java
@Scheduled(cron = "${auction.scheduler.cron}")
@Transactional
public void processAuctions() {
    LocalDateTime now = LocalDateTime.now();
    activateScheduledAuctions(now);
    closeExpiredAuctions(now);  // 분산 락 없이 곧장 상태 변경
}
```

`auction-service/.../application/BidProcessor.java`
```java
public BidResponse process(...) {
    Auction auction = auctionRepository.findById(auctionId)
            .orElseThrow(...);

    if (auction.getAuctionStatus() != AuctionStatus.ACTIVE) {  // endTime 검증 누락
        throw new InvalidAuctionStatusException(...);
    }
    // ...
}
```

#### After
스케줄러도 입찰과 동일한 락을 잡고, 입찰 처리 시 종료 시각도 함께 검증한다.

```java
// AuctionScheduler
private void closeAuction(Auction auction) {
    RLock lock = redissonClient.getLock("auction:bid:lock:" + auction.getId());
    if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
        log.warn("경매 종료 락 획득 실패. auctionId={}", auction.getId());
        return; // 다음 주기에 재시도
    }
    try {
        // 락 획득 후 상태를 다시 읽고 처리 (double-check)
        Auction fresh = auctionRepository.findById(auction.getId()).orElseThrow();
        if (fresh.getAuctionStatus() != AuctionStatus.ACTIVE) return;
        // ...낙찰자 결정 로직
    } finally {
        if (lock.isHeldByCurrentThread()) lock.unlock();
    }
}

// BidProcessor
if (auction.getAuctionStatus() != AuctionStatus.ACTIVE
        || auction.getEndTime().isBefore(LocalDateTime.now())) {
    throw new InvalidAuctionStatusException("종료된 경매에는 입찰할 수 없습니다.");
}
```

#### 트레이드오프
- 스케줄러도 락을 잡으면 종료 처리가 한 사이클 늦어질 수 있다 → 입찰 폭주 시 경매 종료가 1~2초 미뤄지는 정도는 일반적으로 허용 가능.
- 더 강한 보장이 필요하면 DB 비관적 락(`SELECT ... FOR UPDATE`)도 가능하지만, 분산 락 + 트랜잭션 경계로 충분한 경우가 많다.

---

### 1.2 이중 쓰기(Dual Write)와 서비스 간 데이터 정합성

#### 현상
- 경매가 `AUCTION_COMPLETED` 로 종료됐는데 product-service의 상품 상태는 여전히 `ACTIVE`로 남아 있다.
- 상품 상세 화면은 "판매중"인데 경매 화면은 "낙찰 완료"로 보인다.
- 동일 상품에 대한 다른 경매가 또 만들어진다.

#### 원인
`AuctionScheduler.closeAuction()`은 (1) auction 테이블의 상태를 변경하고, (2) RestClient로 product-service에 상태 업데이트를 호출한다. (1)은 로컬 트랜잭션으로 커밋되지만 (2)는 **다른 서비스의 다른 DB**라서 같은 트랜잭션에 묶이지 않는다.

게다가 `ProductServiceClient.updateProductStatus()`는 예외를 잡고 로그만 남긴다 (Best Effort). 즉, 호출이 실패해도 auction 트랜잭션은 정상 커밋되며, 두 서비스의 데이터가 영구적으로 어긋난다.

#### Before
`auction-service/.../infrastructure/client/ProductServiceClient.java`
```java
public void updateProductStatus(Long productId, String status) {
    try {
        productServiceRestClient.patch()
                .uri("/internal/products/{id}/status", productId)
                .body(Map.of("status", status))
                .retrieve()
                .toBodilessEntity();
    } catch (Exception e) {
        log.error("product-service 상태 업데이트 실패. productId={}, status={}, error={}",
                productId, status, e.getMessage());
        // 예외를 삼키므로 호출 측에서 실패를 알 수 없다
    }
}
```

#### After
**Outbox 패턴**: 상태 변경을 로컬 트랜잭션 안에서 outbox 테이블에 기록하고, 별도 폴러가 외부 호출을 책임진다.

```java
// 1) auction 트랜잭션 안에서 outbox에 적재
@Transactional
public void closeAuction(Auction auction) {
    auction.complete(...);
    outboxRepository.save(OutboxEvent.of(
            "PRODUCT_STATUS_UPDATE",
            Map.of("productId", auction.getProductId(),
                   "status",    "AUCTION_COMPLETED",
                   "messageId", UUID.randomUUID().toString())
    ));
}

// 2) 별도 스케줄러가 outbox를 읽어 외부 호출 (재시도 + 멱등성 키 사용)
@Scheduled(fixedDelay = 1000)
public void publish() {
    for (OutboxEvent e : outboxRepository.findUnpublished(50)) {
        try {
            productServiceClient.updateProductStatusIdempotent(
                    e.payload(), e.messageId());
            e.markPublished();
        } catch (Exception ex) {
            e.incrementRetry();   // 백오프 + 최대 재시도
        }
    }
}
```

product-service 측은 `messageId`로 중복 처리를 막는다(Idempotency Key).

#### 트레이드오프
- 즉시 일관성은 포기하고 **최종 일관성(eventual consistency)** 을 받아들인다.
- Outbox 폴러 자체가 멀티 인스턴스에서 중복 실행되면 안 되므로 1.3과 함께 적용해야 한다.
- 본격적으로 가려면 Debezium + Kafka로 outbox CDC 까지 가는 게 정석이지만, 현 규모에서는 폴링만으로도 충분하다.

---

### 1.3 멀티 인스턴스에서 `@Scheduled` 중복 실행

#### 현상
auction-service를 2대 이상 띄우는 순간:
- 경매 종료 처리가 2번 일어나고, 그 결과 결제·알림·product 상태 업데이트 호출이 중복된다.
- 운 좋게 1.1의 분산 락으로 막힐 수도 있지만, 그렇지 않은 코드 경로에서는 데이터가 망가진다.
- 로그에는 "경매 종료 처리: 1건" 이 두 번 찍힌다.

#### 원인
Spring의 `@Scheduled`는 **인스턴스 단위 로컬 스케줄러**다. 클러스터 전체에서 한 번만 실행되도록 보장하는 메커니즘이 없다.

```java
@Scheduled(cron = "${auction.scheduler.cron}")
@Transactional
public void processAuctions() { ... }
```

#### After
**ShedLock**으로 클러스터 단위 락을 잡는다.

`build.gradle`
```groovy
implementation 'net.javacrumbs.shedlock:shedlock-spring:5.16.0'
implementation 'net.javacrumbs.shedlock:shedlock-provider-redis-spring:5.16.0'
```

```java
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT1M")
public class SchedulerLockConfig {
    @Bean
    LockProvider lockProvider(RedisConnectionFactory cf) {
        return new RedisLockProvider(cf, "auction-service");
    }
}

@Scheduled(cron = "${auction.scheduler.cron}")
@SchedulerLock(name = "processAuctions", lockAtMostFor = "PT30S", lockAtLeastFor = "PT5S")
@Transactional
public void processAuctions() { ... }
```

#### 트레이드오프
- `lockAtMostFor`가 너무 짧으면 작업 도중 락이 풀려 다른 인스턴스가 동시에 실행한다 → 가장 긴 작업 시간보다 넉넉히.
- `lockAtLeastFor`는 시계 어긋남(clock skew)으로 인해 한 인스턴스가 너무 빠르게 두 번 잡는 것을 막는다.
- DB 기반 LockProvider도 가능하지만 현 스택은 이미 Redis를 쓰므로 Redis 가 합리적이다.

---

### 1.4 분산 락 대기 타임아웃과 사용자 경험

#### 현상
인기 경매가 마감 직전에 들어가면 사용자 다수가 "입찰 처리 중입니다. 잠시 후 다시 시도해주세요." 메시지를 받는다. 클라이언트는 자동 재시도를 시도하고, 결과적으로 더 큰 부하 폭주가 발생한다.

#### 원인
`BidService.placeBid()`는 락 대기 5초, 점유 10초의 단순 정책을 쓴다.

```java
private static final String BID_LOCK_KEY = "auction:bid:lock:";
// ...
RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
if (!acquired) {
    throw new InvalidAuctionStatusException("입찰 처리 중입니다. 잠시 후 다시 시도해주세요.");
}
```

이 구조의 문제:
1. 한 입찰의 처리 시간이 길수록(예: product-service 호출이 느릴 때) 뒤따르는 입찰이 모조리 실패한다.
2. 비즈니스적으로 정당한 입찰까지 거부된다 (사용자 잘못이 아님).
3. 클라이언트 자동 재시도가 부하를 증폭시킨다.

#### After
세 가지 개선을 함께 적용한다.

**(1) 락 임계 영역 최소화**: 락 안에서는 입찰 검증·저장만 하고, 외부 호출(WebSocket/RestClient)은 락 밖 또는 트랜잭션 커밋 후 이벤트로 분리한다.

```java
public BidResponse process(...) {
    // 락 안: 검증 + DB 저장만
    Bid saved = persistBid(...);
    // 트랜잭션 커밋 후 발행
    eventPublisher.publishEvent(BidPlacedEvent.of(saved));
    return bidMapper.toResponse(saved);
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onBidPlaced(BidPlacedEvent e) {
    messagingTemplate.convertAndSend(...);
    // product-service 호출은 outbox로
}
```

**(2) 백프레셔**: 락 획득 실패 시 즉시 에러 대신 큐에 적재하고 비동기로 처리. 클라이언트에는 "입찰 접수됨" 으로 응답.

**(3) 클라이언트 측**: 자동 재시도 시 지수 백오프 + jitter 적용 (이건 프론트 영역).

#### 트레이드오프
- 락 임계 영역 단축은 거의 부작용 없는 개선이고 가장 먼저 적용해야 한다.
- 비동기 큐잉은 "입찰이 즉시 반영되는가"라는 사용자 기대치와 충돌할 수 있다 → UX 문구를 "접수됨" 으로 바꿔야 함.

---

## 2. 성능 / 안정성

### 2.1 QueryDSL 검색 후 N+1 (LAZY 카테고리·이미지)

#### 현상
- `/products` 검색 API의 응답시간이 결과 수에 비례해서 늘어난다.
- 페이지 크기 100 일 때 SQL 로그에 `select c.* from category where id = ?` 가 100번, `select i.* from product_image where product_id = ?` 가 100번 추가로 찍힌다.
- 카테고리 수가 적으면 DB 캐시로 가려지지만 운영 부하 시 슬로우 쿼리 알림이 폭주한다.

#### 원인
`Product`에서 `category` 와 `images` 가 LAZY 다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(comment = "카테고리 ID")
private Category category;

@OrderBy("sortOrder asc, id asc")
@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
private List<ProductImage> images = new ArrayList<>();
```

`ProductService.searchProducts()` 는 `productMapper::toSummaryResponse` 로 매핑하는데, 매퍼 안에서 `category.name` 과 `extractThumbnailUrl(product)` (= `product.getImages()`) 을 둘 다 접근한다 → 행마다 lazy 초기화 발생.

`application-test.yml` 에는 `default_batch_fetch_size: 100` 이 있지만 운영 프로파일(`application-deploy.yml`) 에는 없을 가능성이 크다.

#### Before
```java
// ProductMapper.java
@Mapping(target = "categoryName", source = "category.name")
@Mapping(target = "thumbnailUrl", expression = "java(extractThumbnailUrl(product))")
ProductSummaryResponse toSummaryResponse(Product product);

default String extractThumbnailUrl(Product product) {
    return product.getImages().stream()  // ← N+1 트리거
            .filter(img -> img.getImageType() == ImageType.THUMBNAIL)
            .findFirst()
            .map(ProductImage::getImageUrl)
            .orElse(null);
}
```

#### After
**옵션 A. DTO Projection (가장 확실)**: 검색 쿼리 자체가 필요한 컬럼만 join 해서 DTO 로 반환.

```java
public Page<ProductSummaryResponse> search(ProductSearchCondition c, Pageable p) {
    QProduct prod = QProduct.product;
    QCategory cat = QCategory.category;
    QProductImage img = QProductImage.productImage;

    JPAQuery<ProductSummaryResponse> q = queryFactory
        .select(Projections.constructor(ProductSummaryResponse.class,
            prod.id, prod.name, prod.startingPrice,
            cat.name,
            JPAExpressions.select(img.imageUrl)
                .from(img)
                .where(img.product.eq(prod).and(img.imageType.eq(ImageType.THUMBNAIL)))
                .orderBy(img.sortOrder.asc()).limit(1),
            prod.createdDate
        ))
        .from(prod)
        .leftJoin(prod.category, cat)
        .where(buildSearchCondition(c));
    // ...
}
```

**옵션 B. `@BatchSize`**: 엔티티 매핑은 그대로 두고 IN 쿼리로 묶기. 가장 손이 적게 가는 임시 처방.

```java
@ManyToOne(fetch = FetchType.LAZY)
@org.hibernate.annotations.BatchSize(size = 100)
private Category category;
```

또한 운영 프로파일에 `hibernate.default_batch_fetch_size: 100` 을 명시한다.

#### 트레이드오프
- DTO Projection 은 가장 빠르지만 검색 쿼리가 화면 요구사항과 결합된다. 화면이 복잡해질수록 매번 새 쿼리가 필요.
- `@BatchSize` / `default_batch_fetch_size` 는 1+1 쿼리로 줄여주지만 0번이 되진 않는다. 화면 정렬·페이지네이션과 무관하게 광범위하게 효과가 있다.
- 둘 다 적용해도 무방하다.

---

### 2.2 동기 RestClient 호출의 카스케이드 장애

#### 현상
product-service 응답이 5초씩 지연되기 시작하자 auction-service 의 톰캣 스레드 풀이 모두 product-service 호출 대기 상태가 되고, 결국 입찰·조회 모든 API가 503 을 반환한다.

#### 원인
`ProductServiceClient` 는 동기 RestClient 를 쓰며, **타임아웃·서킷브레이커·벌크헤드 모두 없다**. 다른 서비스의 장애가 곧장 자기 서비스의 장애가 된다.

```java
public ProductResponse getProduct(Long productId) {
    return productServiceRestClient.get()
            .uri("/products/{id}", productId)
            .retrieve()
            // ... 타임아웃 설정 없음
            .body(ProductResponse.class);
}
```

#### After
**(1) 타임아웃**: `RestClientConfig` 에서 connect/read 타임아웃 명시.

```java
@Bean
RestClient productServiceRestClient(RestClient.Builder builder, ProductClientProps props) {
    SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
    f.setConnectTimeout(Duration.ofMillis(500));
    f.setReadTimeout(Duration.ofSeconds(2));
    return builder.requestFactory(f).baseUrl(props.baseUrl()).build();
}
```

**(2) Resilience4j**: CircuitBreaker + TimeLimiter + Bulkhead.

```groovy
implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
```

```java
@CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
@TimeLimiter(name = "productService")
@Bulkhead(name = "productService", type = Bulkhead.Type.SEMAPHORE)
public ProductResponse getProduct(Long productId) { ... }

private ProductResponse getProductFallback(Long id, Throwable t) {
    throw new ExternalServiceUnavailableException("product-service 일시 장애", t);
}
```

```yaml
resilience4j:
  circuitbreaker:
    instances:
      productService:
        slidingWindowSize: 20
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
  bulkhead:
    instances:
      productService:
        maxConcurrentCalls: 30
```

#### 트레이드오프
- 타임아웃은 부작용이 거의 없는 필수 조치다. 가장 먼저 적용하라.
- 서킷브레이커는 "장애 전파를 빠르게 차단" 하지만, 정상 트래픽 일부도 fallback 으로 빠진다 (false positive). 임계값 튜닝이 필요.
- 벌크헤드는 동시 호출 수를 제한해서 톰캣 스레드 고갈을 막아주는 핵심 안전장치다.

---

### 2.3 다중 서비스 조회 시 응답시간 누적 (현 문서 첫 줄의 그 문제)

#### 현상
경매 상세 화면 한 번을 그리려고 클라이언트가 (1) 경매 조회 (2) 상품 조회 (3) 판매자 조회 (4) 이미지 조회를 직렬로 호출한다. 각 100ms씩만 잡아도 합산 400ms.

서버 사이드에서도 마찬가지여서 `AuctionService.createAuction()`은 product-service 호출이 끝나야 다음 검증으로 넘어간다.

```java
public AuctionDetailResponse createAuction(...) {
    ProductResponse product = productServiceClient.getProduct(request.productId()); // 동기 대기
    if (!"ACTIVE".equals(product.productStatus())) { ... }
    // ...
}
```

#### 원인
- BFF/Aggregator 부재. 각 서비스가 직접 다른 서비스를 호출하는 N+1 호출 패턴.
- 호출이 직렬화돼 있다 (서로 의존성 없는 호출까지도).

#### After
**(1) 서버 사이드 병렬 호출**: 호출 간 의존성 없는 것들은 `CompletableFuture` 로 병렬화.

```java
CompletableFuture<ProductResponse> productF =
        CompletableFuture.supplyAsync(() -> productServiceClient.getProduct(id), ioExecutor);
CompletableFuture<UserResponse> sellerF =
        CompletableFuture.supplyAsync(() -> userServiceClient.getUser(nick), ioExecutor);
CompletableFuture.allOf(productF, sellerF).join();
```

**(2) BFF 패턴**: 화면 단위 응답을 만드는 별도 모듈을 두고 클라이언트는 한 번만 호출.

**(3) Read Model Materialization**: 자주 조회되는 데이터(상품 요약, 판매자 닉네임)를 auction 쪽에 비정규화해서 저장한다. 이미 `Auction.productName`, `productThumbnailUrl`, `sellerNickname` 으로 일부 적용돼 있다 — 이 방향을 더 확대.

#### 트레이드오프
- 비정규화는 데이터 정합성 부담을 늘린다 (1.2 의 outbox 가 같이 가야 함).
- 병렬 호출은 손쉽게 응답시간을 줄여주지만, IO 전용 스레드 풀을 분리하지 않으면 톰캣 스레드를 잡아먹는다 — 2.2 의 벌크헤드와 함께 가야 한다.

---

### 2.4 Testcontainers Redis와 Spring 설정 매핑

#### 현상 (이미 잘 적용된 사례)
Testcontainers 로 Redis 컨테이너를 띄우면 호스트 포트가 매번 바뀐다. `application-test.yml` 에 `spring.data.redis.port: 6379` 같이 정적으로 적어두면 connection refused 가 나거나 로컬에서 실행 중인 다른 Redis 에 붙어 테스트가 새는(leak) 사고가 난다.

#### 원인
Testcontainers 의 `withExposedPorts(6379)` 는 **컨테이너 내부 포트** 를 의미하고, 호스트 측 포트는 동적 할당된다. Spring 측이 이 동적 포트를 읽을 방법이 없으면 매핑이 깨진다.

#### After (현재 코드)
이 프로젝트는 Spring Boot 3.1+ 의 `@ServiceConnection` 으로 깔끔하게 해결하고 있다.

`auction-service/src/test/java/com/pickbit/auctionservice/config/TestContainerConfig.java`
```java
@TestConfiguration
@SuppressWarnings("resource")
public class TestContainerConfig {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4.5"))
                .withDatabaseName("test").withUsername("test").withPassword("test");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }
}
```

#### 만약 Spring Boot 3.1 미만이라면
`@DynamicPropertySource` 로 직접 바인딩한다.

```java
@DynamicPropertySource
static void redisProps(DynamicPropertyRegistry r) {
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
}
```

추가 권장사항:
- 컨테이너는 **클래스마다 새로 띄우지 말고** 싱글톤 패턴으로 JVM 전체에서 공유 (`static` 필드 + `start()` 1회). 테스트 시간이 크게 줄어든다.
- `withReuse(true)` 와 `~/.testcontainers.properties` 의 `testcontainers.reuse.enable=true` 도 로컬 개발 속도에 효과적.

#### 트레이드오프
- `@ServiceConnection` 은 Spring Boot 3.1+ 전용. 현 프로젝트는 충족.
- 컨테이너 재사용은 테스트 격리(데이터 잔존)와 충돌할 수 있어 트랜잭션 롤백 또는 truncate 전략이 필수.

---

## 3. 운영 부수 이슈

### 3.1 파일 업로드 OOM과 ACL 노출

#### 현상
- 사용자가 100MB 이미지를 여러 개 동시에 업로드하면 file-service 가 OOM 으로 죽는다.
- 업로드된 비공개 자료(예: 상품 인증서 사진)도 URL 만 알면 외부에서 그대로 열린다.

#### 원인
`file-service/.../infrastructure/storage/NcpObjectStorageClient.java`
```java
public String upload(String key, InputStream inputStream, String contentType) {
    try {
        byte[] bytes = inputStream.readAllBytes();   // ① 전체 메모리 로드

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(key)
                .contentType(contentType)
                .acl(ObjectCannedACL.PUBLIC_READ)    // ② 모든 파일 공개
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));
    } // ...
}
```

① `readAllBytes()` 는 파일 크기만큼 힙을 점유한다 — 동시 업로드 N개면 N배.
② `PUBLIC_READ` ACL 고정은 권한 모델 자체가 없는 것과 같다.

#### After
```java
public String upload(String key, InputStream inputStream, long contentLength,
                     String contentType, Visibility visibility) {
    PutObjectRequest request = PutObjectRequest.builder()
            .bucket(s3Properties.bucketName())
            .key(key)
            .contentType(contentType)
            .acl(visibility == Visibility.PUBLIC
                    ? ObjectCannedACL.PUBLIC_READ
                    : ObjectCannedACL.PRIVATE)
            .build();

    // 스트리밍 업로드 — 힙에 전체를 쌓지 않음
    s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
    return objectUrl(key);
}

// 비공개 객체는 짧은 만료의 Pre-signed URL 로 제공
public URL presignedGetUrl(String key, Duration ttl) {
    return s3Presigner.presignGetObject(b -> b
            .signatureDuration(ttl)
            .getObjectRequest(g -> g.bucket(...).key(key))).url();
}
```

`MultipartFile` 을 그대로 받지 말고 `getInputStream()` + `getSize()` 로 컨텐츠 길이를 넘긴다. Spring 의 `multipart.max-file-size`, `max-request-size` 도 같이 조이는 것이 안전 그물.

#### 트레이드오프
- AWS SDK v2 의 `RequestBody.fromInputStream` 은 contentLength 를 요구한다. 길이를 모르면 `ContentStreamProvider` 또는 멀티파트 업로드(부분 업로드) 로 대체.
- Pre-signed URL 은 만료까지 누구든 접근 가능하므로 TTL 을 짧게(분 단위).

---

### 3.2 OpenAPI Doc Exporter — `ApplicationReadyEvent` 시점 자기 호출

#### 현상
- 운영 환경에 `openapi.docs-export.enabled=true` 로 켜자 부팅 시간이 늘어나고, 컨테이너 헬스체크 그레이스 타임을 넘으면 ECS/k8s 가 새 인스턴스를 비정상 종료시킨다.
- 헬스체크 게이트가 `ApplicationReadyEvent` 이전이라면 자기 자신 호출이 connection refused 로 실패한다.

#### 원인
```java
@EventListener(ApplicationReadyEvent.class)
public void export() {
    int port = env.getProperty("local.server.port", Integer.class, 8080);
    String url = "http://localhost:" + port + "/v3/api-docs.yaml";
    byte[] bytes = RestClient.create().get().uri(url).retrieve().body(byte[].class);
    Files.write(Path.of("openapi", serviceName, "openapi.yaml"), bytes, ...);
}
```
- 자기 자신을 HTTP로 호출하는 것 자체가 안티패턴 (proxy, ALB, port mapping 변수에 휘둘림).
- 운영 컨테이너 파일시스템에 쓰기 시도(`Files.write`) — 컨테이너가 read-only FS 면 실패.
- 부팅 직후의 디스크 I/O 와 추가 HTTP 핸드셰이크가 헬스체크에 영향.

#### After
**옵션 A. 빌드타임 export 로 전환** (권장): 운영 부팅과 분리.
`springdoc-openapi-gradle-plugin` 또는 CI 에서 dev 인스턴스를 띄워 `/v3/api-docs.yaml` 을 받아 저장하고 git 또는 아티팩트로 보관.

**옵션 B. 런타임 export 를 유지하되 가드 추가**:
```java
@EventListener(ApplicationReadyEvent.class)
public void export() {
    if (!"develop".equals(env.getProperty("spring.profiles.active"))) return; // 운영 차단
    Path out = Path.of(env.getProperty("openapi.docs-export.path", "openapi"));
    // 자기 자신 호출 대신 SpringDocConfigProperties + OpenApiResource 직접 사용
    OpenAPI api = openApiResource.getOpenApi(...);
    Files.write(out.resolve(serviceName + ".yaml"), Yaml.pretty(api).getBytes(), ...);
}
```

#### 트레이드오프
- 빌드타임 분리는 운영 영향이 0 이지만 CI 파이프라인이 한 단계 늘어난다.
- 자기 자신 HTTP 호출은 어떤 경우든 권장하지 않는다 — 같은 JVM 의 Bean 을 직접 부르는 게 항상 안전하다.

---

## 정리

| 분류 | 항목 | 핵심 처방 |
|---|---|---|
| 1.1 | 경매 종료 race | 락 공유 + double-check + endTime 검증 |
| 1.2 | 이중 쓰기 | Outbox + Idempotency Key |
| 1.3 | 스케줄러 중복 | ShedLock + Redis LockProvider |
| 1.4 | 락 대기 UX | 락 임계영역 축소 + 비동기 큐잉 |
| 2.1 | N+1 | DTO Projection or `@BatchSize` |
| 2.2 | 카스케이드 장애 | 타임아웃 + Resilience4j |
| 2.3 | 응답시간 누적 | 병렬 호출 + 비정규화 + BFF |
| 2.4 | Testcontainers 매핑 | `@ServiceConnection` (적용됨) |
| 3.1 | 업로드 OOM/ACL | 스트리밍 + Pre-signed URL |
| 3.2 | OpenAPI exporter | 빌드타임 분리 또는 dev 한정 |

각 사례는 독립적으로도 적용 가능하지만, 1.2(Outbox)는 1.3(ShedLock)과, 2.2(서킷브레이커)는 2.3(병렬 호출)과 함께 갈 때 효과가 가장 크다.

---

## 부록 A. 실제 적용 이력

위 10개 항목 중 현재 리포지토리에 코드로 반영된 항목과 핵심 변경 파일을 정리한다. (✅ = 적용 완료, ⏳ = 미적용)

| 항목 | 상태 | 핵심 변경 |
|---|---|---|
| 1.1 경매 종료 race | ✅ | `AuctionScheduler.closeAuctionWithLock()` — Redisson `auction:bid:lock:{auctionId}` 락 + `findById()` double-check. `BidProcessor.process()` 에 `endTime.isBefore(now())` 검증 추가 |
| 1.2 이중 쓰기 (Outbox) | ✅ (poller 제외) | `OutBoxEvent` 엔티티 + `OutBoxEventRepository` + `OutboxRecorder.record(entity, aggregateId, eventType, payload)`. `library/event/EventBoxIdCreateService` + `EventBoxAutoConfiguration` 으로 `eventId` 생성. `AuctionScheduler` / `BidProcessor` 즉시구매 분기에서 `productServiceClient.updateProductStatus(...)` → `outboxRecorder.record(...)` 로 교체. CDC 발행은 추후 Debezium connector 로 연결 |
| 1.3 스케줄러 중복 | ✅ | `shedlock-spring` + `shedlock-provider-redis-spring` 추가. `SchedulerLockConfig` 에 `RedisLockProvider("auction-service")` 빈 등록. `processAuctions()` 에 `@SchedulerLock(lockAtMostFor=PT30S, lockAtLeastFor=PT5S)` |
| 1.4 락 대기 UX | ⏳ | 입찰 비동기 큐잉/SSE 알림 미적용 |
| 2.1 N+1 | ✅ | `ProductQueryRepository.searchSummary()` 추가 — `Projections.constructor(ProductSummaryResponse, ...)` + `leftJoin(category)` + `JPAExpressions` 썸네일 서브쿼리. `ProductService.searchProducts()` 가 새 메서드 사용 |
| 2.2 카스케이드 장애 | ✅ | `resilience4j-spring-boot3` 추가. `ProductServiceClient.getProduct()` 에 `@CircuitBreaker(productService) + @Bulkhead(SEMAPHORE)`, fallback `getProductFallback()`. `application-{develop,deploy}.yml` 에 슬라이딩 윈도우 20·실패율 50%·OPEN 10s·세마포어 30 설정. RestClient 타임아웃(connect 500ms / read 2s) 도 1차에 적용 |
| 2.3 응답시간 누적 | ⏳ | BFF/병렬 호출 미적용 |
| 2.4 Testcontainers 매핑 | ✅ (선적용) | `@ServiceConnection` 으로 Redis/MySQL 컨테이너 → Spring 자동 매핑 |
| 3.1 업로드 OOM/ACL | ✅ | `NcpObjectStorageClient.upload(key, InputStream, contentLength, contentType, Visibility)` — `RequestBody.fromInputStream(...)` 으로 스트리밍, `Visibility { PUBLIC, PRIVATE }` 분기. `S3Presigner` 빈 + `presignedGetUrl(key, ttl)` 로 PRIVATE 객체 임시 접근. `FileUploadService` 가 `Visibility.PUBLIC` 로 호출 |
| 3.2 OpenAPI exporter | ✅ | `OpenApiDocExporter.isProductionProfile()` 가드 — `deploy/prod/production` 프로파일에서 자기 호출 스킵 |

### 적용 차수별 묶음
- **1차 (안전 변경)**: 2.2 의 RestClient 타임아웃, 1.1 의 `endTime` 검증, 3.2 의 프로덕션 가드, 2.1 의 `default_batch_fetch_size` Hibernate 설정.
- **2차**: 1.1 (스케줄러 분산락 공유 + double-check), 2.1 (DTO Projection), 1.3 (ShedLock), 2.2 (Resilience4j 풀세트).
- **3차 (그룹 2)**: 3.1 (스트리밍 + Visibility 분리 + Pre-signed URL).
- **4차**: 1.2 (Outbox 테이블 적재만 — Debezium CDC 연결은 사용자가 추후 진행, 컨슈머 측 idempotency 도 그때 함께 구현).

### 검증
- `./gradlew :auction-service:compileJava :product-service:compileJava :library:compileJava` BUILD SUCCESSFUL.
- `./gradlew :auction-service:test :product-service:test` 통과 — `BidServiceIntegrationTest` 의 즉시구매 시나리오는 `OutBoxEventRepository.findAll()` 에 `entity=Product / aggregateId=1 / eventType=product.status.update_requested / payload AUCTION_COMPLETED` 행 1건 존재로 재작성, 일반 입찰 시나리오는 outbox 비어 있음으로 단언.

### 남은 항목
- **1.4** 입찰 비동기 큐잉 (Kafka/Disruptor) — 트래픽 패턴이 명확해진 뒤 결정.
- **2.3** BFF/병렬 호출 — `auction-service`가 product-service 외 다른 서비스도 합산 호출하기 시작할 때 도입.

