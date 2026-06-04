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
| `ACCESS_TOKEN` | 없음 | 인증 API 호출에 사용할 Bearer token |
| `RAMP_USERS` | 시뮬레이션별 기본값 | ramp-up 동안 투입할 사용자 수 |
| `RPS` | 조회 시뮬레이션 기본값 | 초당 요청 사용자 주입률 |
| `TPS` | 입찰 시뮬레이션 기본값 | 초당 입찰 사용자 주입률 |
| `RAMP_SECONDS` | 시뮬레이션별 기본값 | ramp-up 시간 |
| `DURATION_SECONDS` | 시뮬레이션별 기본값 | steady-state 시간 |
| `BID_BASE_AMOUNT` | `10000` | 입찰 테스트 시작 금액 |
| `BID_INCREMENT` | `1000` | 입찰 요청마다 증가시킬 금액 |

## 사전 준비

Gateway를 기준으로 테스트하므로 테스트 대상 서버가 먼저 실행되어 있어야 한다.

로컬 Gateway 예시:

```bash
BASE_URL=http://localhost:18080
```

develop Gateway 예시:

```bash
BASE_URL=http://192.168.20.70:18080
```

인증이 필요한 시뮬레이션은 로그인 후 발급받은 access token이 필요하다.

```bash
ACCESS_TOKEN=eyJ...
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

```bash
BASE_URL=http://localhost:18080 \
ACCESS_TOKEN=eyJ... \
AUCTION_ID=1 \
BID_BASE_AMOUNT=10000 \
BID_INCREMENT=1000 \
RAMP_USERS=10 \
TPS=5 \
./gradlew :load-test:gatlingRun \
  --simulation com.pickbit.loadtest.AuctionBidSimulation
```

주의:

- `AUCTION_ID`는 `ACTIVE` 상태여야 한다.
- `ACCESS_TOKEN` 사용자는 해당 경매의 판매자가 아니어야 한다.
- 테스트 중 입찰 금액은 `BID_BASE_AMOUNT`부터 `BID_INCREMENT`만큼 계속 증가한다.
- 단일 경매에 동시 입찰을 거는 테스트라 일부 요청은 입찰가 검증 실패로 `400` 또는 경합 상황에서 `409`가 발생할 수 있다.
- 테스트 후 DB에서 최종 `currentPrice`, `WINNING`/`ACTIVE` 입찰 상태, payment 생성 여부를 별도로 확인해야 한다.

기본 assertion:

- p95 응답 시간 `< 1000ms`

## 결과 위치

Gatling HTML 리포트는 아래 경로에 생성된다.

```text
load-test/build/reports/gatling
```

## 권장 테스트 순서

1. `ProductReadSimulation`으로 기본 조회 성능 확인
2. `AuctionReadSimulation`으로 경매 조회 성능 확인
3. `MyPageSimulation`으로 인증 API 성능 확인
4. `AuctionBidSimulation`으로 같은 경매 입찰 경합 성능 확인

처음에는 작은 부하로 시작하고 점진적으로 올린다.

```text
10 users -> 50 users -> 100 users -> 300 users
```

## 권장 목표치

- 조회 API p95 `< 500ms`
- 마이페이지 API p95 `< 700ms`
- 입찰 API p95 `< 1000ms`
- 실패율 `< 1%`

입찰 API는 단순 성공률보다 데이터 정합성도 함께 확인해야 한다.
