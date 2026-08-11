# Auction Bid Load Test Analysis

이 문서는 `AuctionBidSimulation`으로 실행한 입찰 API 부하 테스트 결과를 Gatling 리포트와 `bid.csv` DB 덤프 기준으로 정리한다.

> **2026-06-18 시점의 기록이다. 이후 구조와 테스트 설정이 모두 바뀌었다.**
>
> - 입찰 경로가 Redisson 분산락 + 동기 트랜잭션 → **Redis Lua 중재 + 비동기 영속화**로 바뀌었다
>   ([bid-arbitration-design.md](./bid-arbitration-design.md))
> - `bidders.csv`(미리 발급한 JWT)는 제거되고 게이트웨이 API key 방식으로 대체됐다
> - 당시 시뮬레이션은 `429`를 성공으로 셌고 실패율 단언이 없었다. 지금은 `429`/`5xx`가 실패다
> - 게이트웨이 rate limit 이 사용자당 2/s → 10/s 로 완화됐다
>
> **여기 기록된 24.33 req/s 는 개선 전 기준선으로만 쓴다.** 재현하려면 위 문서를 따를 것.

## 분석 대상

대상 API:

```http
POST /api/auctions/{AUCTION_ID}/bids
```

관련 코드:

| 구분 | 파일 |
| --- | --- |
| Gatling 시뮬레이션 | `load-test/src/gatling/java/com/pickbit/loadtest/AuctionBidSimulation.java` |
| bidder 토큰 CSV | `load-test/src/gatling/resources/bidders.csv` |
| API Controller | `auction-service/src/main/java/com/pickbit/auctionservice/api/BidController.java` |
| 입찰 락/트랜잭션 | `auction-service/src/main/java/com/pickbit/auctionservice/application/BidCommandService.java` |
| 입찰 검증/저장 | `auction-service/src/main/java/com/pickbit/auctionservice/application/BidProcessor.java` |
| Gateway rate limit | `gateway-service/src/main/resources/application.yml` |

분석한 Gatling 리포트:

| 실행 디렉터리 | 실행 시각(GMT) | Duration |
| --- | --- | --- |
| `auctionbidsimulation-20260618232126545` | `2026-06-18 23:21:27` | `29s` |
| `auctionbidsimulation-20260618232507645` | `2026-06-18 23:25:08` | `2m 29s` |

DB 덤프:

```text
/Users/jonghun/Documents/bid.csv
```

`bid.csv`는 헤더가 없는 `bid` 테이블 덤프 형태로 보이며, 컬럼은 아래 순서로 해석했다.

```text
id, created_date, modified_date, amount, bid_status, bid_time, bidder_nickname, bidder_user_id, auction_id
```

## 테스트 입력 조건

`AuctionBidSimulation`은 `bidders.csv`의 `accessToken`을 순환 사용한다.

확인 결과:

| 항목 | 값 |
| --- | --- |
| CSV 총 라인 수 | `21` |
| 데이터 라인 수 | `20` |
| 빈 토큰 | `0` |
| 중복 토큰 | `0` |
| 사용자 ID 범위 | `4` ~ `23` |
| 닉네임 | `lt-bid-0830-1` ~ `lt-bid-0830-20` |
| 토큰 발급 시각 | `2026-06-18 23:08:31~34 GMT` |
| 토큰 만료 시각 | `2026-06-18 23:38:31~34 GMT` |

두 Gatling 실행 시각이 모두 토큰 만료 전이므로, 이번 테스트 결과는 인증 토큰 만료 문제로 보이지 않는다.

## 시뮬레이션 동작

입찰 요청 body는 매 요청마다 금액을 증가시킨다.

```json
{
  "bidAmount": 50000
}
```

`AuctionBidSimulation`은 아래 HTTP 상태를 모두 Gatling 성공(OK)으로 처리한다.

```java
.check(status().in(201, 400, 409, 429))
```

상태 코드 의미:

| 상태 | 의미 |
| --- | --- |
| `201` | 입찰 성공, `bid` row 생성 |
| `400` | 요청 validation 실패 가능성 |
| `409` | 입찰가 부족, 경매 상태 오류, 락 획득 실패, 낙관적 락 충돌 등 |
| `429` | Gateway 사용자별 rate limit 초과 |

따라서 Gatling 리포트의 `OK`는 모든 요청이 입찰에 성공했다는 뜻이 아니다. 이 테스트에서는 `201/400/409/429`가 모두 기대 가능한 응답 범위다.

## API 보호 장치

입찰 API에는 세 단계 보호 장치가 있다.

| 보호 장치 | 위치 | 역할 |
| --- | --- | --- |
| Gateway rate limit | `RequestRateLimiter` | 사용자별 과도한 입찰 요청 차단 |
| Redis 분산 락 | `auction:bid:lock:{auctionId}` | 같은 경매 입찰을 직렬화 |
| JPA optimistic lock | `Auction.@Version` | 스케줄러/취소 등 교차 경로와의 DB 충돌 방어 |

Gateway rate limit 설정:

```yaml
redis-rate-limiter.replenishRate: 2
redis-rate-limiter.burstCapacity: 5
redis-rate-limiter.requestedTokens: 1
```

20명의 bidder 토큰을 사용하므로, 사용자별 rate limit을 기준으로 전체 지속 처리량은 이론상 약 `40 TPS`까지 가능하다. 버스트는 사용자별 최대 `5`건까지 허용된다.

## Gatling 리포트 결과

### auctionbidsimulation-20260618232126545

| 지표 | 값 |
| --- | ---: |
| 총 요청 수 | `45` |
| Gatling OK | `45` |
| Gatling KO | `0` |
| 평균 요청 수 | `1.5 req/s` |
| min | `23ms` |
| p50 | `31ms` |
| p75 | `32ms` |
| p95 | `41ms` |
| p99 | `117ms` |
| max | `117ms` |
| assertion | p95 `< 1000ms` 통과 |

### auctionbidsimulation-20260618232507645

| 지표 | 값 |
| --- | ---: |
| 총 요청 수 | `3650` |
| Gatling OK | `3650` |
| Gatling KO | `0` |
| 평균 요청 수 | `24.33 req/s` |
| min | `10ms` |
| p50 | `15ms` |
| p75 | `17ms` |
| p95 | `28ms` |
| p99 | `64ms` |
| max | `183ms` |
| assertion | p95 `< 1000ms` 통과 |

두 리포트 모두 Gatling 기준 실패는 없고, 입찰 API 목표치인 p95 `< 1000ms`를 충분히 만족한다.

## bid.csv 검증 결과

`bid.csv` 전체 집계:

| 항목 | 값 |
| --- | ---: |
| 전체 row 수 | `3698` |
| id 범위 | `1` ~ `3698` |
| bid_time 범위 | `2026-06-18 12:32:51.923034` ~ `2026-06-19 08:27:38.140362` |
| `OUTBID` | `3693` |
| `WINNING` | `4` |
| `ACTIVE` | `1` |

auction별 집계:

| auction_id | row 수 | 상태 분포 | 최대 입찰가 |
| --- | ---: | --- | ---: |
| `1` | `2` | `OUTBID=1`, `WINNING=1` | `350000.00` |
| `2` | `1` | `WINNING=1` | `1500.00` |
| `3` | `1` | `WINNING=1` | `1000.00` |
| `4` | `1` | `WINNING=1` | `200.00` |
| `5` | `3693` | `OUTBID=3692`, `ACTIVE=1` | `3849000.00` |

부하 테스트 입찰은 모두 `auction_id=5`에 저장되었다.

## 리포트와 DB 저장 row 대조

Gatling 리포트 시각은 GMT이고, `bid.csv`의 `bid_time`은 로컬 시간으로 보인다. 따라서 리포트 시각에 `+9h`를 적용해서 비교했다.

### 첫 번째 실행

대상:

```text
auctionbidsimulation-20260618232126545
```

비교 구간:

```text
2026-06-19 08:21:27 ~ 2026-06-19 08:21:56
```

DB 저장 결과:

| 항목 | 값 |
| --- | ---: |
| Gatling 요청 수 | `45` |
| DB 저장 입찰 수 | `45` |
| bid id 범위 | `6` ~ `50` |
| amount 범위 | `51000.00` ~ `95000.00` |
| bidder 수 | `20` |
| 상태 | `OUTBID=45` |

현재는 이후 두 번째 테스트의 더 높은 입찰에 밀려 모두 `OUTBID` 상태다.

### 두 번째 실행

대상:

```text
auctionbidsimulation-20260618232507645
```

비교 구간:

```text
2026-06-19 08:25:08 ~ 2026-06-19 08:27:38
```

DB 저장 결과:

| 항목 | 값 |
| --- | ---: |
| Gatling 요청 수 | `3650` |
| DB 저장 입찰 수 | `3648` |
| bid id 범위 | `51` ~ `3698` |
| amount 범위 | `200000.00` ~ `3849000.00` |
| bidder 수 | `20` |
| 상태 | `OUTBID=3647`, `ACTIVE=1` |

두 번째 실행은 Gatling 요청 `3650`건 중 DB에 저장된 입찰이 `3648`건이다. 즉 최소 `2`건은 `bid` row 생성까지 가지 않았다.

가능한 원인:

- Gateway rate limit으로 `429` 응답
- 입찰 처리 경합 또는 검증 실패로 `409` 응답
- 요청 body validation 실패로 `400` 응답

Gatling 설정상 `400/409/429`도 OK로 처리되므로, 리포트의 `KO=0`과 DB 저장 row 차이는 모순이 아니다.

## 최종 정합성 확인

auction `5`의 최종 활성 입찰:

| 항목 | 값 |
| --- | --- |
| bid id | `3698` |
| amount | `3849000.00` |
| bid_status | `ACTIVE` |
| bidder_nickname | `lt-bid-0830-10` |
| bidder_user_id | `13` |
| bid_time | `2026-06-19 08:27:38.140362` |

정합성 관점에서 확인된 점:

- auction `5`에 `ACTIVE` 입찰은 정확히 `1`개만 남았다.
- 이전 입찰 `3692`건은 `OUTBID`로 전환되었다.
- 최종 `ACTIVE` 입찰 금액은 auction `5`의 최대 입찰가와 일치한다.
- 같은 경매에 대한 동시 입찰이 대량으로 들어왔지만, 최고 입찰 상태가 중복되지 않았다.

## 결론

이번 입찰 부하 테스트는 응답 시간과 데이터 정합성 모두 양호하게 보인다.

성능 관점:

- 약 `24.33 req/s` 수준의 두 번째 테스트에서도 p95가 `28ms`로 측정되었다.
- 목표치 p95 `< 1000ms`를 크게 만족했다.
- Gatling 기준 KO는 두 실행 모두 `0`이다.

정합성 관점:

- 부하 테스트 대상 auction `5`의 최종 `ACTIVE` 입찰이 1개만 남았다.
- 최종 최고가 `3849000.00`이 `ACTIVE` 상태로 유지되었다.
- 이전 입찰들은 `OUTBID`로 정리되었다.

해석상 주의점:

- Gatling OK는 비즈니스 성공(`201`)만 의미하지 않는다.
- 두 번째 실행에서 요청 수와 DB 저장 row 수가 `2`건 차이난다.
- 상태 코드별 실제 분포는 Gatling HTML만으로는 확인하기 어렵고, 서버 access log나 Gateway log를 함께 남기면 더 정확히 분석할 수 있다.

## 다음 테스트 개선 제안

다음 부하 테스트에서는 아래를 같이 남기면 분석 정확도가 올라간다.

- Gatling에서 상태 코드별 카운트를 별도 그룹명으로 분리한다.
- Gateway access log에서 `201/400/409/429` 분포를 남긴다.
- 테스트 시작 전후 auction row를 덤프해서 `current_price`와 최종 `ACTIVE` bid 금액을 직접 대조한다.
- 테스트별 `AUCTION_ID`, `BID_BASE_AMOUNT`, `BID_INCREMENT`, `RAMP_USERS`, `TPS`, `RAMP_SECONDS`, `DURATION_SECONDS`를 리포트와 함께 기록한다.

상태 코드별 분리 예시:

```java
.check(status().saveAs("httpStatus"))
```

또는 성공 입찰만 엄격히 보고 싶은 테스트에서는 `201`만 OK로 보고, `409/429`는 별도 시나리오에서 관찰하는 방식으로 분리한다.
