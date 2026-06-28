# 입찰 트래픽 안정성

Pickbit은 경매 전용 마켓플레이스이고, 시스템에서 가장 뜨거운 경로는 결국 **입찰 API**다. 그동안 Redisson 분산 락으로 같은 경매에 대한 입찰을 직렬화하고, DB 커밋 이후에 락을 풀도록 정리하면서 같은 경매에서의 동시 입찰 문제는 어느 정도 정리되었다.

그런데 입찰 흐름을 다시 한 번 트래픽 관점에서 점검해보니, 락만으로는 닫히지 않는 구멍이 두 개 더 보였다.

- **입찰이 아닌 다른 경로**(스케줄러의 경매 종료, 판매자 취소)도 같은 `Auction` 행을 건드린다. 입찰 락은 입찰끼리만 직렬화하기 때문에 이 교차 경로는 DB 레벨에서 보호되지 않는다.
- **입찰 API 자체에 호출 빈도 제한이 없다**. 한 사용자가 의도적으로 또는 봇으로 초당 수십~수백 건을 던지면, 다른 정상 사용자의 입찰이 락 큐 뒤에서 계속 대기 후 실패한다. 사실상 1인 DoS가 가능했다.

이 문서는 두 구멍을 어떻게 닫았는지에 대한 기록이다.

---

## 1. 입찰 외 경로와의 동시성

### 1.1 Redisson 락은 입찰 경로만 직렬화한다

기존 락 구조는 `auction:bid:lock:{auctionId}` 키로 입찰 처리를 직렬화한다. 즉 같은 경매에 들어오는 두 입찰 요청은 안전하게 한 명씩 처리된다.

그런데 `Auction` 행을 수정하는 코드 경로는 입찰만 있는 게 아니다.

- `AuctionScheduler.closeExpiredAuctions()` — 종료 시각이 지난 경매를 ACTIVE → ENDED 로 전환
- `AuctionCommandService.cancelAuction()` — 판매자가 경매를 취소 (SCHEDULED 상태에서)
- 그리고 `BidProcessor.process()` 안에서 buy-now 가격 충족 시 `auction.complete(...)` 호출

스케줄러는 자신만의 락(`tryLock(0s)`)으로 보호되지만, 이 락은 **입찰 락과 같은 키 공간이 아니다**. 결국 두 경로가 같은 row에 동시에 접근할 수 있는 윈도우가 남는다.

예시 시나리오:

```text
1. A가 입찰 락 획득 → currentPrice 갱신 중
2. 같은 순간, 스케줄러가 endTime 도달을 감지 → 별도 락으로 close 시도
3. 두 트랜잭션이 같은 Auction 행을 거의 동시에 UPDATE
4. 둘 중 한 쪽의 변경이 다른 쪽에 묻혀버릴 가능성
```

이런 상황은 빈도가 매우 낮지만, 발생하면 경매 종료 직전 마지막 입찰이 누락되거나, 종료된 경매에 입찰이 한 번 더 들어가는 식의 정합성 문제로 이어진다.

---

### 1.2 JPA `@Version`으로 DB 레벨 낙관적 잠금 추가

문제의 본질은 “애플리케이션 락은 동일 키만 지킨다”는 점이다. 서로 다른 락을 쓰는 경로가 같은 행을 만지는 한, 락만으로는 부족하다.

가장 적은 변경으로 닫을 수 있는 방법이 **JPA `@Version` 컬럼**이다. Hibernate가 UPDATE 시점에 자동으로 `WHERE version = ?` 조건을 붙이고, 0건 영향이면 `ObjectOptimisticLockingFailureException`을 던진다. 누가 락을 잡았든 안 잡았든, DB가 마지막 방어선이 된다.

`Auction` 엔티티에만 추가했다. `BaseEntity`에 넣으면 모든 서비스의 모든 테이블이 영향을 받기 때문에 범위가 과하다. `Bid`는 `markOutbid`가 벌크 SQL이고 `markWinning`은 입찰 락 안에서만 호출되므로 굳이 추가하지 않았다.

```java
// auction-service/src/main/java/com/pickbit/auctionservice/domain/Auction.java
@Version
@Column(nullable = false)
private Long version;
```

호출부 코드는 한 줄도 바꾸지 않았다. Hibernate가 dirty checking → flush 시점에 자동으로 검사하기 때문이다.

충돌이 감지되면 사용자에게 `409 Conflict`로 알린다. 그래서 Spring이 던진 `ObjectOptimisticLockingFailureException`을 잡는 핸들러를 하나 추가했다.

```java
// auction-service/src/main/java/com/pickbit/auctionservice/exception/AuctionExceptionHandler.java
@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
protected ResponseEntity<ProblemDetail> handleOptimisticLock(...) {
    return buildResponse(HttpStatus.CONFLICT,
            "동시 처리 충돌이 발생했습니다. 잠시 후 다시 시도해주세요.", request);
}
```

### 1.3 재시도는 일부러 넣지 않았다

처음에는 Spring Retry로 `@Retryable`을 붙여 충돌 시 자동 재시도하는 안도 고민했지만, 두 가지 이유로 빼기로 했다.

- Redisson 락 덕분에 입찰끼리는 직렬화되므로 충돌은 (입찰↔스케줄러), (입찰↔취소) 두 교차 경로에서만 발생한다 → 빈도가 매우 낮다.
- 재시도를 락 안쪽에 넣으면 의미가 없고, 락 바깥에 넣으면 락을 다시 잡아야 해서 트랜잭션 경계가 복잡해진다. Spring Retry 의존성도 새로 들어온다.

지금은 그냥 409로 올려보내고, 클라이언트가 짧게 기다렸다가 재시도하는 정책으로 둔다. 운영 데이터를 보고 충돌이 실제로 잦으면 그때 재시도를 도입하는 게 가성비가 좋다.

### 1.4 검증

낙관적 잠금이 실제로 동작하는지 통합 테스트로 확인한다. 두 스레드가 같은 `Auction`을 stale 상태로 로드한 뒤 거의 동시에 `saveAndFlush`를 호출하면, 한 쪽은 성공하고 다른 쪽은 `ObjectOptimisticLockingFailureException`이 발생해야 한다.

```text
auction-service/src/test/.../BidServiceIntegrationTest.java
  > OptimisticLockConcurrency
      > detects_stale_write
      > redisson_lock_prevents_version_conflict
```

두 번째 테스트는 회귀 방지용이다. 정상 흐름(`BidCommandService`)을 두 스레드가 동시에 호출했을 때 Redisson 락이 직렬화해주므로 버전 충돌이 일어나면 안 된다는 걸 명시적으로 검증한다.

---

## 2. 입찰 API에 호출 빈도 제한

### 2.1 락만으로는 트래픽 폭주를 막을 수 없었다

같은 경매에 대한 입찰은 락으로 줄 세우고 있다. 그런데 한 사용자가 같은 경매에 초당 100건을 던지면 어떻게 될까.

```text
사용자 A: 초당 100건 POST /api/auctions/10/bids
1. 첫 요청 → Redisson 락 획득 → 처리 중
2. 99건의 다른 요청 → 락 대기 (최대 5초)
3. 그 사이에 들어온 사용자 B의 정상 입찰 → 역시 대기열 뒤
4. 5초가 지나면 대기 중인 요청은 "입찰 처리 중입니다. 잠시 후 다시 시도해주세요" 응답
```

A가 의도적이든 봇이든, 인기 경매 하나를 사실상 자기 혼자 점유할 수 있다. 다른 사용자는 정상적으로 입찰을 시도해도 자꾸 5초 타임아웃에 걸려서 실패한다. 락은 동시성을 정리할 뿐, 호출 빈도 자체를 막아주지는 않는다.

### 2.2 Gateway 레벨에서 막는 게 맞는 자리였다

이 문제는 본질적으로 “악성/오동작 트래픽을 서비스 앞단에서 잘라내는” 문제다. 그래서 두 가지 후보가 있었다.

- 서비스 레벨에서 Resilience4j `@RateLimiter` 적용
- Gateway에서 Spring Cloud Gateway의 `RequestRateLimiter` 필터 적용

서비스 레벨 라이미터는 인스턴스 로컬에서 카운팅한다. `auction-service`가 두 대 떠있다면 사용자 A가 인스턴스 1, 인스턴스 2에 각각 5건씩 보내도 둘 다 통과한다. 수평 확장 시 카운팅이 어긋난다.

Gateway는 단일 chokepoint이고, Redis를 통해 카운팅하므로 인스턴스 수와 무관하다. 그리고 Spring Cloud Gateway 2025.1.0이 이미 들어 있어서 `RequestRateLimiter`는 추가 의존성 없이 바로 쓸 수 있었다. Redis 클라이언트만 하나 더 붙이면 된다.

```groovy
// gateway-service/build.gradle
implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
```

### 2.3 키는 사용자 단위, IP는 fallback

레이트 리밋의 기준이 되는 키를 무엇으로 잡을지가 핵심이다. IP 단위는 같은 사무실/카페에서 들어오는 정상 사용자들이 같이 차단된다. 사용자 단위가 자연스럽다.

이 프로젝트는 이미 `AuthenticationGlobalFilter`(order -2)가 JWT를 검증하고 사용자 식별자를 `X-User-Id` 헤더로 주입해서 downstream으로 흘려보낸다. 입찰 엔드포인트는 인증 필수 경로라서 정상 흐름에서는 항상 이 헤더가 채워진다.

`KeyResolver`는 사용자 ID 우선, 헤더가 비어있는 비정상 케이스에만 IP로 fallback 한다.

```java
// gateway-service/src/main/java/com/pickbit/gatewayservice/config/RateLimiterConfig.java
@Bean
public KeyResolver userKeyResolver() {
    return exchange -> {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (StringUtils.hasText(userId)) {
            return Mono.just("user:" + userId);
        }
        // ... IP fallback
    };
}
```

`RequestRateLimiter`는 라우트 필터로 동작한다. 글로벌 필터인 `AuthenticationGlobalFilter`가 먼저 실행되므로, 라우트 필터가 돌 때는 이미 `X-User-Id`가 채워져 있다.

### 2.4 라우트는 입찰 POST만 좁게 잡았다

Gateway 라우팅은 Consul 기반 동적 라우트가 `/api/auctions/**` 전체를 받고 있다. 여기에 레이트 리밋을 거는 대신, 더 좁은 정적 라우트를 하나 만들어서 입찰 POST만 잘라내는 방식을 택했다.

```yaml
# gateway-service/src/main/resources/application.yml
- id: auction-bid-ratelimit
  uri: lb://auction-service
  order: -1
  predicates:
    - Path=/api/auctions/*/bids
    - Method=POST
  filters:
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: 2
        redis-rate-limiter.burstCapacity: 5
        redis-rate-limiter.requestedTokens: 1
        key-resolver: "#{@userKeyResolver}"
```

`order: -1`로 동적 라우트(기본 order 0)보다 먼저 매칭되도록 했다. Path가 더 구체적이라서 매칭이 우선되긴 하지만, order를 명시하는 게 더 명확하다.

이렇게 잡으니 `GET /api/auctions/{id}/bids`(입찰 이력 조회)나 `POST /api/auctions`(경매 생성)에는 영향이 없다.

### 2.5 수치는 yml에 외부화

`replenishRate=2`, `burstCapacity=5`로 시작했다. 토큰 버킷 방식이고, 풀어 쓰면 다음과 같다.

```text
- 사용자마다 버킷이 있다. 최대 5개 토큰을 담을 수 있다.
- 초당 2개씩 토큰이 채워진다.
- 입찰 요청 1건당 토큰 1개를 소비한다.
- 버킷이 비면 429 Too Many Requests로 응답한다.
```

사람이 손가락으로 누를 수 있는 한계는 초당 1~2회 정도다. 인기 경매 마감 직전 스나이핑이 들어와도 5건 버스트면 충분히 받아낼 수 있다. 너무 보수적인 값이면 정상 사용자도 차단될 수 있으므로 운영 데이터를 보면서 yml만 수정해서 조정한다. 코드 재빌드는 필요 없다.

응답 헤더에 다음 정보가 자동으로 붙어서 클라이언트가 현재 상태를 알 수 있다.

```text
X-RateLimit-Remaining       : 남은 토큰
X-RateLimit-Burst-Capacity  : 최대 버킷 크기
X-RateLimit-Replenish-Rate  : 초당 보충 속도
```

---

## 3. 두 해결책의 역할 분담

`@Version`과 Rate Limit은 서로 다른 문제를 푼다.

| 구분 | 대상 | 책임 |
|---|---|---|
| Rate Limit (Gateway) | 트래픽 양 | 한 사용자가 너무 자주 보내지 못하게 막는다. 악성/오동작 트래픽을 서비스 앞단에서 잘라낸다. |
| Redisson 락 | 같은 경매 입찰 동시성 | 입찰끼리 순서대로 처리되도록 직렬화한다. 같은 currentPrice를 두 트랜잭션이 동시에 읽는 것을 막는다. |
| `@Version` | DB 행 동시성 | 입찰이 아닌 경로(스케줄러/취소)와의 교차 동시성을 DB 레벨에서 마지막으로 막는다. |

세 가지가 겹쳐서 작동한다. Rate Limit이 막지 못한 정상 트래픽은 Redisson 락이 처리하고, 락이 커버하지 못한 교차 경로는 `@Version`이 잡는다.

---

## 4. 운영 시 주의

`@Version` 컬럼은 develop 환경(`ddl-auto: update`)에서는 Hibernate가 자동으로 추가한다. 그러나 deploy 환경은 `ddl-auto: validate`이라서 컬럼이 없으면 애플리케이션이 뜨지 않는다. 배포 전에 수기 DDL이 필요하다.

```sql
ALTER TABLE auction ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
```

Gateway는 이번 변경으로 Redis 의존이 추가됐다. 기존에는 Redis가 없어도 떴지만, 이제 Redis가 떠 있지 않으면 `RequestRateLimiter`가 동작하지 않는다. 환경별 Redis 호스트/포트 설정이 각 프로파일 yml에 있는지 확인해야 한다(`spring.data.redis.host`, `spring.data.redis.port`).
