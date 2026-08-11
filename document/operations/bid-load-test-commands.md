# 부하 테스트 실행 가이드

Gatling 시뮬레이션 실행 명령 모음입니다. 설계 배경은
[bid-arbitration-design.md](../engineering/bid-arbitration-design.md) 를 참고하세요.

---

## 1. 사전 준비

### 1-1. API key 켜기

부하 테스트는 게이트웨이의 테스트용 API key 로 인증합니다. 사용자 수백 명분의 JWT 를
미리 발급할 필요가 없어집니다. (`document/operations/api-key-testing.md`)

```bash
# 로컬(develop)은 기본 활성. 키만 secrets/application-develop-secret.yml 에 넣으면 된다.
openssl rand -hex 32          # 이 값을 GATEWAY_API_KEY 로

# EC2(deploy)는 기본 비활성이므로 측정할 때만 켠다.
#   .env 에 GATEWAY_API_KEY_ENABLED=true
docker compose -f docker-compose.deploy.yml up -d --no-deps gateway-service
```

> 측정이 끝나면 deploy 에서는 다시 꺼두세요.

### 1-2. 테스트 데이터

시뮬레이션은 아래를 가정합니다. 없으면 400/404 가 대량 발생합니다.

| 항목 | 기본값 | 환경변수 |
|---|---|---|
| 진행 중(ACTIVE) 경매 | ID 1 ~ 20 | `AUCTION_ID_BASE`, `AUCTION_COUNT` |
| 상품 | ID 1 | `PRODUCT_ID` |
| 입찰자 사용자 | ID 1000 ~ 1499 | `BIDDER_ID_BASE`, `BIDDER_COUNT` |

입찰자는 **경매 판매자와 달라야** 합니다. 판매자 본인 입찰은 거절됩니다.

### 1-3. 환경변수

```bash
export BASE_URL=http://localhost:18080          # EC2: https://api.pickbit.co.kr
export API_KEY=<GATEWAY_API_KEY 와 같은 값>
```

---

## 2. 시뮬레이션

| 시뮬레이션 | 목적 | 기본 부하 |
|---|---|---|
| `MixedLoadSimulation` | **합산 1000 rps 목표 측정** (읽기 80 / 인증 15 / 입찰 5) | 1000 rps, 3분 |
| `MultiAuctionBidSimulation` | 경매 20개 분산 입찰 | 500 TPS, 1분 |
| `AuctionBidSimulation` | 단일 경매 입찰 상한 | 100 TPS, 1분 |
| `ProductReadSimulation` | 상품 조회만 | 20 rps |
| `AuctionReadSimulation` | 경매 조회만 | 20 rps |
| `MyPageSimulation` | 인증 조회만 | 10 rps |

```bash
./gradlew :load-test:gatlingRun-com.pickbit.loadtest.MixedLoadSimulation
./gradlew :load-test:gatlingRun-com.pickbit.loadtest.MultiAuctionBidSimulation
./gradlew :load-test:gatlingRun-com.pickbit.loadtest.AuctionBidSimulation
```

리포트: `load-test/build/reports/gatling/`

### 부하 조절

```bash
# 합산 목표를 바꿔서 어디서 무너지는지 찾기
TOTAL_RPS=500  ./gradlew :load-test:gatlingRun-com.pickbit.loadtest.MixedLoadSimulation
TOTAL_RPS=1000 ./gradlew :load-test:gatlingRun-com.pickbit.loadtest.MixedLoadSimulation
TOTAL_RPS=1500 ./gradlew :load-test:gatlingRun-com.pickbit.loadtest.MixedLoadSimulation

# 단일 경매 상한 찾기
TPS=200 DURATION_SECONDS=60 ./gradlew :load-test:gatlingRun-com.pickbit.loadtest.AuctionBidSimulation
TPS=500 DURATION_SECONDS=60 ./gradlew :load-test:gatlingRun-com.pickbit.loadtest.AuctionBidSimulation

# 중재 끄고 개선 전 수치 재기 (비교용)
#   .env 에 AUCTION_BID_ARBITER_ENABLED=false 후 auction-service 재기동
```

---

## 3. 성공/실패 판정이 바뀌었습니다

이전 `AuctionBidSimulation` 은 `status().in(201, 400, 409, 429)` 로 **429까지 성공으로 셌고**
실패율 단언도 없었습니다. rate limit 에 전부 막혀도 테스트가 통과했다는 뜻입니다.

지금은 이렇게 나눕니다.

| 응답 | 판정 | 이유 |
|---|---|---|
| 201 | 성공 | |
| 400 | 성공으로 계수 | 동시 입찰에서 늦게 도착한 낮은 금액이 거절되는 것은 정상 경합 |
| **429** | **실패** | rate limit 에 막힌 것이므로 처리량 측정이 무효 |
| **5xx** | **실패** | 서버가 처리하지 못함 |

`global().failedRequests().percent().lt(1.0)` 단언이 붙어 있으므로 rate limit 이나 서버 오류가
1% 를 넘으면 테스트가 실패합니다.

> **429 가 나오면** `BIDDER_COUNT` 를 늘리세요. 게이트웨이 rate limit 은 사용자당 10/s 이므로
> 목표 TPS ÷ 10 이상의 입찰자가 필요합니다. 1000 TPS 를 입찰로만 채우려면 100명 이상입니다.

---

## 4. 측정 후 정합성 검증

**처리량 숫자만 보고 끝내지 마세요.** 비동기 영속화라 데이터가 어긋날 수 있습니다.

```sql
-- 경매당 ACTIVE 입찰은 정확히 1개여야 한다 (결과가 없어야 정상)
SELECT auction_id, COUNT(*) FROM bid WHERE bid_status = 'ACTIVE'
GROUP BY auction_id HAVING COUNT(*) <> 1;

-- auction.current_price 가 최고 입찰가와 일치해야 한다 (결과가 없어야 정상)
SELECT a.id, a.current_price, MAX(b.amount) AS top_bid
FROM auction a JOIN bid b ON b.auction_id = a.id
GROUP BY a.id, a.current_price HAVING a.current_price <> MAX(b.amount);

-- 이벤트 순번에 중복이 없어야 한다 (결과가 없어야 정상)
SELECT auction_id, sequence, COUNT(*) FROM auction_event
GROUP BY auction_id, sequence HAVING COUNT(*) > 1;
```

```bash
# 스트림에 처리되지 않은 입찰이 남아 있지 않아야 한다
docker exec pickbit-deploy-redis redis-cli XPENDING auction:bid:stream auction-bid-persistence

# Redis 현재가와 DB 가 일치해야 한다
docker exec pickbit-deploy-redis redis-cli HGET auction:state:1 currentPriceMinor
```

Gatling 이 보고한 201 건수와 `bid` 테이블 증가분이 일치하는지도 확인하세요.

---

## 5. 자원 확인

측정 중 다른 터미널에서:

```bash
docker stats --no-stream
```

- **CPU 가 80% 를 지속하면** 인스턴스가 천장입니다. m7g.2xlarge 로 올리고 재측정하세요.
- **Hikari 대기가 있으면** 커넥션 풀이 부족합니다:
  `curl localhost:18085/actuator/metrics/hikaricp.connections.pending`
- **Redis 캐시 적중률**: `docker exec pickbit-deploy-redis redis-cli INFO stats | grep keyspace`

---

## 6. 기록

측정할 때마다 `document/engineering/` 에 개선 전/후를 남기세요.
기존 기록: `auction-bid-load-test-analysis.md` (2026-06-18, 최고 24.33 req/s).
