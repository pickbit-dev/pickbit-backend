# 판매자 정산

## 흐름

```
구매확정 (POST /api/payments/{id}/confirm-purchase)
  -> Settlement 생성 (PENDING, 수수료 미계산 상태)
정산 배치 (10분 주기, ShedLock)
  -> 플랫폼 수수료 5% / PG 수수료 차감
  -> netSellerAmount 확정, COMPLETED 로 전이
판매자 조회 (GET /api/settlements/me)
```

## 무엇이 문제였나

**배치가 계산한 정산 행을 읽을 수 있는 경로가 아예 없었습니다.** 컨트롤러도, 조회 서비스도,
DTO 도 없었고 `SettlementRepository` 에는 `findByPaymentId` 와 (아무도 호출하지 않는)
`findByStatus` 두 개뿐이었습니다. 판매자는 얼마를 언제 받는지 확인할 방법이 없었습니다.

더 근본적으로 **`Settlement` 에 `sellerUserId` 컬럼이 없었습니다.** "내 정산 내역"을
Payment 와 조인하지 않고는 조회조차 불가능한 구조였습니다. 애초에 읽히도록 설계되지
않았다는 뜻입니다.

그리고 **`FAILED` 는 영원히 재시도되지 않았습니다.** 배치 리더가 `status = PENDING` 만
조회했기 때문입니다. 상태 설명에는 `"정산 실패 (재시도 필요)"` 라고 적혀 있었는데도
재시도하는 코드가 없었습니다. 즉 한 번 실패한 정산은 **판매자가 돈을 못 받는 채로 방치**됐습니다.

## 지금 구조

### 엔티티에 추가된 것

| 컬럼 | 이유 |
|---|---|
| `sellerUserId` | 정산 조회의 기본 키. 없으면 조회 자체가 불가능 |
| `auctionId`, `productId`, `productName`, `productThumbnailUrl` | 목록 표시용 스냅샷. 매번 Payment 와 조인하지 않기 위함 |
| `retryCount` | 실패 재시도 횟수. 상한을 넘으면 배치가 더 집어가지 않는다 |

인덱스 두 개를 함께 넣었습니다.
- `(seller_user_id, status)` — 판매자별 조회
- `(status, retry_count)` — 배치 리더

### API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/settlements/me?status=&page=&size=` | 내 정산 목록 (최신순, 상태 필터 선택) |
| GET | `/api/settlements/me/summary` | 상태별 건수와 금액 합계 |
| GET | `/api/settlements/{settlementId}` | 정산 상세 (본인 것만) |

상세 조회는 `Settlement.isOwnedBy()` 로 소유자를 검증하고, 아니면 403 을 돌려줍니다.

요약 응답은 마이페이지 상단에 한 줄로 쓰라고 만든 것입니다.

```json
{
  "pendingCount": 1,  "pendingAmount": 47500.00,
  "completedCount": 3, "completedAmount": 142500.00,
  "failedCount": 0,   "failedAmount": 0
}
```

게이트웨이 라우팅은 payment-service 의 Consul 메타데이터에 경로를 추가해 열었습니다.

```yaml
gateway-path: /api/payments/**,/api/settlements/**
```

### 실패 정산 재시도

배치 리더가 `PENDING` 과 **재시도 여지가 남은 `FAILED`** 를 함께 집어갑니다.

```sql
select s from Settlement s
where s.status = :pending
   or (s.status = :failed and s.retryCount < :maxRetries)
order by s.id asc
```

`markFailed()` 가 호출될 때마다 `retryCount` 가 올라가고, `max-retries`(기본 5)를 넘으면
배치가 더 이상 집어가지 않습니다. 계속 실패하는 건이 매 주기 배치를 잡아먹는 것을 막기
위함이며, 그 시점부터는 사람이 확인해야 하는 상태입니다.

| 프로퍼티 | 기본값 |
|---|---|
| `payment.settlement-batch.cron` | `0 */10 * * * *` |
| `payment.settlement-batch.chunk-size` | `100` |
| `payment.settlement-batch.platform-fee-rate` | `0.05` |
| `payment.settlement-batch.pg-fee-rate` | `0.00` |
| `payment.settlement-batch.max-retries` | `5` |

## 운영 확인

```sql
-- 재시도 상한을 넘겨 방치된 정산 (사람이 봐야 함)
SELECT id, payment_id, seller_user_id, net_seller_amount, retry_count, failure_reason
FROM settlement
WHERE status = 'FAILED' AND retry_count >= 5;

-- 아직 정산되지 않은 총액
SELECT COALESCE(SUM(net_seller_amount), 0) FROM settlement WHERE status <> 'COMPLETED';

-- 구매확정됐는데 정산 행이 없는 결제 (있으면 안 된다)
SELECT p.id FROM payment p
LEFT JOIN settlement s ON s.payment_id = p.id
WHERE p.status = 'PURCHASE_CONFIRMED' AND s.id IS NULL;
```

진단용 리포지토리 메서드도 있습니다: `countAbandoned(maxRetries)`, `sumOutstanding()`.

## 남은 것

- **프론트엔드에 정산 화면이 없습니다.** API 는 준비됐지만 마이페이지에 붙이는 작업이 남았습니다.
- 재시도 상한을 넘긴 정산에 **자동 알림이 없습니다.** 위 쿼리나 Grafana 로 확인해야 합니다.
  `library` 에 Slack 클라이언트가 있으므로 연결할 수 있습니다.
- 정산 지급 실행(실제 송금)은 범위 밖입니다. 지금은 정산 금액 계산과 상태 관리까지입니다.
