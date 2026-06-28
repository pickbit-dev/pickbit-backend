# Bid Load Test Commands

이 파일은 `AuctionBidSimulation` 실행에 필요한 명령어 모음이다.

## 1. Gateway 상태 확인

```bash
curl -i http://localhost:18080/actuator/health
```

## 2. bidder CSV 확인

`AuctionBidSimulation`은 아래 파일의 access token을 순환 사용한다.

```bash
wc -l load-test/src/gatling/resources/bidders.csv
```

정상 예시:

```text
21 load-test/src/gatling/resources/bidders.csv
```

첫 줄은 헤더이고, 나머지 20줄이 bidder token이다.

## 3. Gatling 컴파일 확인

```bash
./gradlew :load-test:compileGatlingJava
```

## 4. 가벼운 입찰 테스트

`AUCTION_ID`는 실제 `ACTIVE` 상태 경매 ID로 바꿔야 한다.

```bash
BASE_URL=http://localhost:18080 \
AUCTION_ID=123 \
BID_BASE_AMOUNT=50000 \
BID_INCREMENT=1000 \
RAMP_USERS=5 \
TPS=2 \
RAMP_SECONDS=10 \
DURATION_SECONDS=20 \
./gradlew :load-test:gatlingRun \
  --simulation com.pickbit.loadtest.AuctionBidSimulation
```

## 5. 기본 입찰 경쟁 테스트

```bash
BASE_URL=http://localhost:18080 \
AUCTION_ID=123 \
BID_BASE_AMOUNT=50000 \
BID_INCREMENT=1000 \
RAMP_USERS=20 \
TPS=10 \
RAMP_SECONDS=20 \
DURATION_SECONDS=60 \
./gradlew :load-test:gatlingRun \
  --simulation com.pickbit.loadtest.AuctionBidSimulation
```

## 6. 강한 입찰 경쟁 테스트

Gateway rate limit은 사용자당 초당 2건, burst 5건이다. bidder 20명 기준 전체 지속 TPS는 약 40 TPS까지 가능하다.

```bash
BASE_URL=http://localhost:18080 \
AUCTION_ID=123 \
BID_BASE_AMOUNT=50000 \
BID_INCREMENT=1000 \
RAMP_USERS=50 \
TPS=30 \
RAMP_SECONDS=30 \
DURATION_SECONDS=120 \
./gradlew :load-test:gatlingRun \
  --simulation com.pickbit.loadtest.AuctionBidSimulation
```

## 7. Rate Limit 확인 테스트

이 테스트는 429가 섞이는지 확인하는 용도다.

```bash
BASE_URL=http://localhost:18080 \
AUCTION_ID=123 \
BID_BASE_AMOUNT=50000 \
BID_INCREMENT=1000 \
RAMP_USERS=100 \
TPS=60 \
RAMP_SECONDS=20 \
DURATION_SECONDS=60 \
./gradlew :load-test:gatlingRun \
  --simulation com.pickbit.loadtest.AuctionBidSimulation
```

## 8. 리포트 위치

```text
load-test/build/reports/gatling
```

## 9. 테스트 전 체크리스트

- `AUCTION_ID`는 `ACTIVE` 상태여야 한다.
- `bidders.csv`의 사용자는 경매 판매자가 아니어야 한다.
- `BID_BASE_AMOUNT`는 현재 경매가보다 높아야 한다.
- `BID_INCREMENT`는 최소 입찰 단위보다 커야 한다.
- 400은 입찰 금액/상태 검증 실패일 수 있다.
- 409는 동시성 충돌 또는 입찰 처리 경합일 수 있다.
- 429는 Gateway rate limit 초과다.

## 10. 테스트 후 확인

- 경매의 최종 `currentPrice`가 가장 높은 성공 입찰가와 맞는지 확인한다.
- `WINNING` 상태 입찰이 하나만 있는지 확인한다.
- 이전 입찰들이 `OUTBID` 처리됐는지 확인한다.
- buy-now 가격 도달 시 경매 종료와 payment 생성이 중복되지 않았는지 확인한다.
