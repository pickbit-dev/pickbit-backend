# Gatling Load Test

이 문서는 `load-test` 모듈로 Gateway 기준 API 부하 테스트를 실행하는 방법을 정리한다.

## 목적

- 조회 API의 처리량과 응답 시간을 측정한다.
- 마이페이지 API의 인증 기반 조회 성능을 측정한다.
- 단일 경매 입찰 요청이 몰릴 때 응답 시간과 실패 상태를 관찰한다.
- 기능 정합성 테스트가 아니라 실제 HTTP 트래픽 기반 성능 테스트로 사용한다.

## 모듈 구조

```text
load-test
└── src/gatling/java/com/pickbit/loadtest
    ├── LoadTestConfig.java
    ├── ProductReadSimulation.java
    ├── AuctionReadSimulation.java
    ├── MyPageSimulation.java
    └── AuctionBidSimulation.java
```

## 공통 환경변수

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `BASE_URL` | `http://localhost:18080` | Gateway base URL |
| `PRODUCT_ID` | `1` | 상세 조회에 사용할 상품 ID |
| `AUCTION_ID` | `1` | 상세 조회/입찰에 사용할 경매 ID |
| `API_KEY` | 없음 | **권장 인증 방식.** 게이트웨이 테스트용 API key. 사용자 ID 를 헤더로 지정하므로 사용자별 토큰이 필요 없다 |
| `ACCESS_TOKEN` | 없음 | `API_KEY` 가 없을 때의 폴백. 단일 사용자 Bearer token |
| `BIDDER_COUNT` | `500` | 서로 다른 입찰자 수. rate limit(사용자당 10/s) 때문에 목표 TPS ÷ 10 이상이어야 한다 |
| `BIDDER_ID_BASE` | `1000` | 입찰자 사용자 ID 시작값 |
| `AUCTION_ID_BASE` | `1` | 다중 경매 시나리오의 경매 ID 시작값 |
| `AUCTION_COUNT` | `20` | 다중 경매 시나리오에서 사용할 경매 수 |
| `TOTAL_RPS` | `1000` | `MixedLoadSimulation` 의 합산 목표 처리량 |
| `RAMP_USERS` | 시뮬레이션별 기본값 | ramp-up 동안 투입할 사용자 수 |
| `RPS` | 조회 시뮬레이션 기본값 | 초당 요청 사용자 주입률 |
| `TPS` | 입찰 시뮬레이션 기본값 | 초당 입찰 사용자 주입률 |
| `RAMP_SECONDS` | 시뮬레이션별 기본값 | ramp-up 시간 |
| `DURATION_SECONDS` | 시뮬레이션별 기본값 | steady-state 시간 |
| `BID_BASE_AMOUNT` | `10000` | 입찰 테스트 시작 금액 |
| `BID_INCREMENT` | `1000` | 입찰 요청마다 증가시킬 금액 |

> **`bidders.csv` 는 제거됐습니다.** 미리 발급한 JWT 20개를 넣어두는 방식이었는데 토큰이
> 만료되면 전체 테스트가 무용지물이 됐고(실제로 2026-06-18 만료), 인원을 늘리려면 그만큼
> 토큰을 다시 발급해야 했습니다. 지금은 `API_KEY` + `BIDDER_COUNT` 로 대체합니다.

## 사전 준비

Gateway를 기준으로 테스트하므로 테스트 대상 서버가 먼저 실행되어 있어야 한다.

로컬 Gateway 예시:

```bash
BASE_URL=http://localhost:18080
```

EC2(deploy) 예시:

```bash
BASE_URL=https://api.pickbit.co.kr
```

인증이 필요한 시뮬레이션은 게이트웨이 API key 를 씁니다. 켜는 방법과 주의사항은
[api-key-testing.md](../operations/api-key-testing.md) 를 참고하세요.

```bash
export API_KEY=<GATEWAY_API_KEY 와 같은 값>
```

## 컴파일 검증

```bash
./gradlew :load-test:compileGatlingJava
```

## 시뮬레이션 실행

특정 시뮬레이션 실행:

```bash
./gradlew :load-test:gatlingRun \
  --simulation com.pickbit.loadtest.ProductReadSimulation
```

모든 시뮬레이션 순차 실행:

```bash
./gradlew :load-test:gatlingRun --all --non-interactive
```

## 상품 조회 테스트

대상 API:

```http
GET /api/products
GET /api/products/{PRODUCT_ID}
```

실행:

```bash
BASE_URL=http://localhost:18080 \
PRODUCT_ID=1 \
RAMP_USERS=50 \
RPS=20 \
./gradlew :load-test:gatlingRun \
  --simulation com.pickbit.loadtest.ProductReadSimulation
```

기본 assertion:

- 실패율 `< 1%`
- p95 응답 시간 `< 500ms`

## 경매 조회 테스트

대상 API:

```http
GET /api/auctions
GET /api/auctions/{AUCTION_ID}
GET /api/auctions/products/{PRODUCT_ID}
```

실행:

```bash
BASE_URL=http://localhost:18080 \
AUCTION_ID=1 \
PRODUCT_ID=1 \
RAMP_USERS=50 \
RPS=20 \
./gradlew :load-test:gatlingRun \
  --simulation com.pickbit.loadtest.AuctionReadSimulation
```

기본 assertion:

- 실패율 `< 1%`
- p95 응답 시간 `< 500ms`

## 마이페이지 테스트

대상 API:

```http
GET /api/products/me/selling
GET /api/payments/me
```

실행:

```bash
BASE_URL=http://localhost:18080 \
ACCESS_TOKEN=eyJ... \
RAMP_USERS=20 \
RPS=10 \
./gradlew :load-test:gatlingRun \
  --simulation com.pickbit.loadtest.MyPageSimulation
```

기본 assertion:

- 실패율 `< 1%`
- p95 응답 시간 `< 700ms`

## 입찰 테스트

대상 API:

```http
POST /api/auctions/{AUCTION_ID}/bids
```

실행:

`AuctionBidSimulation`은 `BIDDER_COUNT` 명의 사용자를 순환하면서 같은 경매에 입찰한다.
`MultiAuctionBidSimulation`은 같은 부하를 `AUCTION_COUNT` 개 경매에 분산한다.

```bash
BASE_URL=http://localhost:18080 \
API_KEY=$GATEWAY_API_KEY \
AUCTION_ID=1 \
BIDDER_COUNT=500 \
TPS=100 \
./gradlew :load-test:gatlingRun-com.pickbit.loadtest.AuctionBidSimulation
```

주의:

- `AUCTION_ID`는 `ACTIVE` 상태여야 한다.
- 입찰자 사용자(`BIDDER_ID_BASE` ~ `+BIDDER_COUNT`)는 해당 경매의 판매자가 아니어야 한다.
- 게이트웨이 rate limit 이 사용자당 10/s 이므로 **`BIDDER_COUNT` 는 목표 TPS ÷ 10 이상**이어야 한다.
  부족하면 `429`가 대량 발생하고 테스트가 실패한다.
- 테스트 중 입찰 금액은 `BID_BASE_AMOUNT`부터 `BID_INCREMENT`만큼 계속 증가한다.
- 단일 경매 동시 입찰이라 늦게 도착한 낮은 금액은 `400`으로 거절된다. 이는 정상 경합이므로
  실패로 세지 않는다. **`429`와 `5xx`는 실패로 계수한다** — rate limit 에 막혔거나 서버가
  처리하지 못한 것이므로 처리량 측정 자체가 무효다.
- 테스트 후 반드시 정합성을 검증한다. 비동기 영속화라 처리량 숫자만으로는 부족하다.
  쿼리는 [bid-load-test-commands.md](../operations/bid-load-test-commands.md) 4장 참고.

기본 assertion:

- 실패율(429/5xx) `< 1%`
- p95 응답 시간 `< 1000ms`

## 결과 위치

Gatling HTML 리포트는 아래 경로에 생성된다.

```text
load-test/build/reports/gatling
```

## 권장 테스트 순서

1. `ProductReadSimulation` / `AuctionReadSimulation` 으로 조회 성능 확인
2. `MyPageSimulation` 으로 인증 API 성능 확인
3. `AuctionBidSimulation` 으로 **단일 경매** 입찰 상한 확인
4. `MultiAuctionBidSimulation` 으로 **경매 분산 시** 입찰 처리량 확인
5. `MixedLoadSimulation` 으로 **합산 1000 rps** 목표 측정

처음에는 작은 부하로 시작하고 점진적으로 올린다.

```bash
TOTAL_RPS=250 -> 500 -> 1000 -> 1500
```

## 권장 목표치

| 구간 | 목표 |
|---|---|
| 공개 조회 (비로그인 GET) | 1000 rps, p95 `< 200ms` |
| 인증 포함 혼합 | 1000 rps, p95 `< 300ms` |
| 입찰 — 단일 경매 | 500+ TPS |
| 입찰 — 경매 20개 합산 | 1000+ TPS |
| 실패율 (429/5xx) | `< 1%` |

기준선은 `auction-bid-load-test-analysis.md` 의 **24.33 req/s** (2026-06-18, 개선 전)이다.
- 실패율 `< 1%`

입찰 API는 단순 성공률보다 데이터 정합성도 함께 확인해야 한다.
