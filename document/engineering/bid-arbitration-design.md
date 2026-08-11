# 입찰 경로 설계 — Redis 중재 + 비동기 영속화

## 왜 바꿨나

기존 구조는 경매 단위 Redisson 분산락을 **DB 트랜잭션 전체 동안** 붙들었습니다.

```
lock(auction:bid:lock:{id})
  ├ SELECT auction
  ├ SELECT bid  (ORDER BY amount DESC — 그 경매의 모든 입찰을 정렬)
  ├ UPDATE bid  (직전 ACTIVE -> OUTBID)
  ├ INSERT bid
  ├ UPDATE auction
  └ INSERT auction_event + flush
unlock()
```

한 경매의 입찰이 완전히 직렬화되므로 **처리량 = 1 / (락 획득 + 트랜잭션 시간)** 이 됩니다.
5~10ms 라면 경매 하나당 100~200/s 가 상한이고, 커넥션 풀을 아무리 키워도 올라가지 않습니다.

게다가 두 번째 SELECT 는 "입찰이 하나라도 있나"만 확인하는데 그 경매의 입찰 전체를
정렬하고 있었습니다. **입찰이 쌓일수록 입찰이 느려지는** 구조였습니다.
(실측: 3650건이 쌓인 경매에서 마지막 입찰은 3650행을 정렬)

## 지금 구조

```
[요청] --> Lua 스크립트 1회 왕복 (~0.2ms)
             ├ 상태/시각/판매자/금액 검증
             ├ currentPrice 갱신, seq 발급
             └ XADD auction:bid:stream
         --> 실시간 이벤트 즉시 발행 (DB 대기 없음)
         --> 응답

[워커] XREADGROUP (배치 200) --> 한 트랜잭션에 묶어 INSERT/UPDATE --> XACK
```

Redis 왕복 한 번으로 검증과 현재가 갱신이 원자적으로 끝나므로 단일 경매의 이론 상한이
수천/s 로 올라갑니다.

### 핵심 파일

| 파일 | 역할 |
|---|---|
| `auction-service/src/main/resources/redis/place-bid.lua` | 검증 + 현재가 갱신 + 스트림 적재를 원자적으로 |
| `infrastructure/redis/BidArbiter.java` | 스크립트 실행 |
| `infrastructure/redis/AuctionStateStore.java` | 경매 상태 해시 관리, 복구, 순번 발급 |
| `application/BidPersistenceWorker.java` | 스트림 소비 루프 (단일 소비자) |
| `application/BidBatchPersister.java` | 경매별로 묶어 한 트랜잭션에 기록 |
| `application/AuctionSequenceAllocator.java` | 순번 발급 (Redis 우선, 없으면 DB) |

### 상태 해시 `auction:state:{auctionId}`

`status`, `currentPriceMinor`, `minIncrementMinor`, `startingPriceMinor`, `buyNowPriceMinor`,
`endTimeEpochMs`, `sellerUserId`, `hasBid`, `seq`, `persistedSeq`

금액은 전부 **minor unit(원 × 100) 정수**입니다. Lua 는 실수 연산만 제공하므로 금액을 그대로
넘기면 비교와 덧셈에 오차가 생깁니다. 스키마가 `scale 2` 라 100을 곱하면 항상 정확한 정수가
되고, Lua 가 정확히 다루는 범위(2^53) 안에 충분히 들어옵니다. (`MinorUnits`, 테스트 있음)

## 설계상 주의점

### 순번(sequence)이 이벤트 ID가 됐다

입찰이 비동기로 기록되면서 `auction_event.id`(auto-increment) 순서가 실제 입찰 순서를
보장하지 못하게 됐습니다. 그래서 **Redis 가 입찰 수락 시점에 발급하는 순번**을 이벤트 ID로
씁니다. WebSocket 실시간 이벤트와 `GET /api/auctions/{id}/events?afterEventId=` 가 같은 값을
쓰므로 클라이언트의 누락 이벤트 복구는 그대로 동작합니다 (프론트 변경 없음).

즉시 구매가 도달 시 입찰 이벤트와 종료 이벤트는 **서로 다른 순번**을 받습니다.
같은 순번을 공유하면 `afterEventId` 복구가 둘 중 하나를 건너뜁니다.

### 경매 종료는 드레인을 기다린다

`AuctionScheduler` 는 Redis 상태를 먼저 닫아 추가 입찰을 막고, `persistedSeq == seq` 가 될
때까지 기다린 뒤 낙찰자를 정합니다. 기다리지 않으면 **아직 DB에 기록되지 않은 최고 입찰을
놓친 채로 낙찰자를 뽑게 됩니다.** 타임아웃(기본 10초)을 넘기면 종료하지 않고 다음 주기로
넘깁니다 — 잘못된 낙찰자를 만드는 것보다 늦는 편이 낫습니다.

### Redis 재시작 복구

상태 키가 없으면 Lua 가 `NOT_LOADED` 를 돌려주고, `BidCommandService` 가 DB에서 복구한 뒤
한 번 재시도합니다. 순번은 `auction_event` 의 마지막 순번에서 이어갑니다.
경매 활성화 시점(`AuctionScheduler.activateScheduledAuctions`)에도 미리 올려둡니다.

## 감수한 것

이 설계는 다음을 명시적으로 맞바꿉니다.

1. **최대 1초치 입찰 유실 가능.** Redis AOF 가 `appendfsync everysec` 기본값이므로 Redis 프로세스가
   비정상 종료하면 아직 fsync 되지 않은 스트림 항목이 사라질 수 있습니다. 포트폴리오 수준에서는
   수용 가능하다고 판단했습니다. 허용할 수 없다면 `appendfsync always` 로 바꿔야 하고,
   그러면 처리량 이점의 상당 부분을 잃습니다.

2. **Redis 가 단일 장애점이 됐습니다.** 락·캐시·rate limiter·ShedLock 에 더해 진행 중 경매의
   현재가까지 올라갑니다. 그래서 메모리를 256MB → 1GB 로 올리고 스트림에
   `MAXLEN ~ 100000` 상한을 걸었습니다. `maxmemory-policy` 는 `noeviction` 이라
   가득 차면 입찰이 거절됩니다 — 조용히 사라지는 것보다 낫습니다.

3. **MySQL 의 `auction.current_price` 가 잠깐 뒤처집니다.** 경매 상세 조회는 Redis 상태를
   우선 봐야 정확합니다. 종료 시점에는 드레인을 기다리므로 최종 상태는 일치합니다.

4. **입찰 응답의 `id` 가 `null` 입니다.** DB 기록 전에 응답하기 때문입니다.
   클라이언트는 순번으로 이벤트를 잇습니다.

## 되돌리는 법

```bash
# .env
AUCTION_BID_ARBITER_ENABLED=false
docker compose -f docker-compose.deploy.yml up -d --no-deps auction-service
```

기존 분산락 + 동기 트랜잭션 경로(`BidProcessor`)가 그대로 남아 있어 재배포 없이 되돌릴 수
있습니다. 중재 경로에 문제가 생겼을 때를 위한 스위치입니다.

## 검증 방법

부하 테스트 후 아래가 전부 성립해야 합니다.

```sql
-- 1. 경매당 ACTIVE 입찰은 정확히 1개
SELECT auction_id, COUNT(*) FROM bid WHERE bid_status = 'ACTIVE' GROUP BY auction_id HAVING COUNT(*) <> 1;

-- 2. auction.current_price 가 최고 입찰가와 일치 (드레인 완료 후)
SELECT a.id, a.current_price, MAX(b.amount)
FROM auction a JOIN bid b ON b.auction_id = a.id
GROUP BY a.id, a.current_price HAVING a.current_price <> MAX(b.amount);

-- 3. 순번에 중복이 없다
SELECT auction_id, sequence, COUNT(*) FROM auction_event GROUP BY auction_id, sequence HAVING COUNT(*) > 1;
```

```bash
# 4. Redis 현재가와 DB 가 일치
docker exec pickbit-deploy-redis redis-cli HGET auction:state:{id} currentPriceMinor

# 5. 스트림에 처리되지 않은 항목이 남아 있지 않다
docker exec pickbit-deploy-redis redis-cli XPENDING auction:bid:stream auction-bid-persistence
```

**장애 주입도 함께 하세요.** 부하 중 `docker restart pickbit-deploy-redis` 를 실행하고
경매가 DB에서 복구되어 계속 동작하는지 확인합니다.
