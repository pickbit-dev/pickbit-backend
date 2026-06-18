# 프론트 결제/거래 테스트 가이드

## Base URL

```text
개발: http://localhost:18080
배포: https://api.pickbit.co.kr
```

## 인증

```http
Authorization: Bearer {accessToken}
```

쿠키 기반 로그인 환경이면 기존 로그인 쿠키 방식을 그대로 사용합니다.

---

## 1. 경매 생성 후 상품 상태 확인

판매자가 상품 등록 후 경매를 생성합니다.

```http
POST /api/auctions
Content-Type: application/json
Authorization: Bearer {sellerAccessToken}
```

```json
{
  "productId": 1,
  "startingPrice": 10000,
  "buyNowPrice": 100000,
  "minimumBidIncrement": 1000,
  "startTime": "2026-06-18 10:00:00",
  "endTime": "2026-06-18 18:00:00"
}
```

기대 상태:

```text
AuctionStatus: SCHEDULED
ProductStatus: AUCTION_SCHEDULED
```

경매 생성 직후 상품이 바로 `AUCTION_SCHEDULED`로 예약됩니다. 프론트는 해당 상품의 수정/삭제 버튼을 비활성화하면 됩니다.

---

## 2. 낙찰 후 결제 대기 목록 확인

구매자 계정으로 확인합니다.

```http
GET /api/payments/me?paymentType=REQUIRED
Authorization: Bearer {buyerAccessToken}
```

기대 상태:

```text
REQUESTED 또는 PG_PENDING
```

---

## 3. 결제 요청 정보 조회

```http
GET /api/payments/{paymentId}/request-info
Authorization: Bearer {buyerAccessToken}
```

응답 예시:

```json
{
  "paymentId": 1,
  "pgOrderId": "order-xxx",
  "amount": 10000,
  "orderName": "상품명",
  "customerKey": "customer-100",
  "successUrl": "...",
  "failUrl": "...",
  "paymentDeadlineAt": "2026-06-19T18:00:00"
}
```

프론트는 이 값으로 Toss 결제창을 띄우면 됩니다.

---

## 4. Toss 결제 성공 후 백엔드 Confirm

Toss 성공 redirect 또는 SDK 성공 callback에서 받은 값으로 호출합니다.

```http
POST /api/payments/confirm
Content-Type: application/json
Authorization: Bearer {buyerAccessToken}
```

```json
{
  "paymentKey": "{tossPaymentKey}",
  "orderId": "{pgOrderId}",
  "amount": 10000
}
```

기대 응답:

```json
{
  "paymentId": 1,
  "status": "ESCROWED",
  "paidAt": "2026-06-18T12:00:00",
  "confirmDeadlineAt": "2026-06-25T12:00:00"
}
```

기대 상태:

```text
PaymentStatus: ESCROWED
ProductStatus: TRADE_IN_PROGRESS
```

프론트 표시:

```text
결제 완료
거래 진행 중
구매확정 가능
```

---

## 5. 구매확정

구매자가 물건 확인 후 호출합니다.

```http
POST /api/payments/{paymentId}/confirm-purchase
Authorization: Bearer {buyerAccessToken}
```

요청 body는 없습니다.

기대 응답:

```json
{
  "paymentId": 1,
  "status": "RELEASED",
  "releasedAt": "2026-06-18T12:30:00"
}
```

기대 상태:

```text
PaymentStatus: RELEASED
ProductStatus: SOLD
```

프론트 표시:

```text
거래 완료
판매 완료
```

이미 `RELEASED` 상태에서 다시 호출해도 성공 응답을 반환합니다. 버튼 중복 클릭이나 네트워크 재시도에 안전합니다.

---

## 6. 자동 구매확정

수동 구매확정을 하지 않으면 결제 완료 시각 기준 7일 후 자동 구매확정됩니다.

```text
paidAt + 7일 후 자동 구매확정
```

자동 처리 후 기대 상태:

```text
PaymentStatus: RELEASED
ProductStatus: SOLD
```

프론트는 `confirmDeadlineAt`을 보고 자동 구매확정 예정일을 표시하면 됩니다.

---

## 7. 환불

구매자가 환불 요청을 호출합니다.

```http
POST /api/payments/{paymentId}/refund
Content-Type: application/json
Authorization: Bearer {buyerAccessToken}
```

```json
{
  "reason": "환불 사유"
}
```

기대 상태:

```text
PaymentStatus: REFUNDED
ProductStatus: INACTIVE
```

프론트 표시:

```text
환불 완료
상품 비활성화
```

---

## 8. 결제 전 포기

결제하기 전 낙찰자가 결제를 포기합니다.

```http
POST /api/payments/{paymentId}/cancel-before-pay
Authorization: Bearer {buyerAccessToken}
```

요청 body는 없습니다.

기대 상태:

```text
PaymentStatus: CANCELLED
ProductStatus: ACTIVE
```

프론트 표시:

```text
결제 포기 완료
상품 재판매 가능
```

---

## PaymentStatus 매핑

```text
REQUESTED: 결제 대기
PG_PENDING: 결제 승인 처리 중
ESCROWED: 결제 완료 / 거래 진행 중 / 구매확정 가능
RELEASED: 거래 완료
REFUNDED: 환불 완료
FAILED: 결제 실패
CANCELLED: 결제 취소 또는 결제 전 포기
DISPUTED: 분쟁 진행 중
```

## ProductStatus 매핑

```text
ACTIVE: 판매 가능
AUCTION_SCHEDULED: 경매 예정
IN_AUCTION: 경매 진행 중
AUCTION_COMPLETED: 낙찰 완료, 결제 대기 또는 결제 처리 전
TRADE_IN_PROGRESS: 결제 완료, 거래 진행 중
SOLD: 판매 완료
INACTIVE: 비활성화
DELETED: 삭제됨
```

---

## 프론트 테스트 체크리스트

```text
경매 생성 직후 상품 수정/삭제 버튼 비활성화
결제 완료 후 구매확정 버튼 노출
confirmDeadlineAt 표시
구매확정 후 버튼 숨김
RELEASED 상태 재호출에도 화면 깨지지 않음
환불 후 상품 INACTIVE 표시
결제 포기/미결제 만료 후 상품 ACTIVE 표시
```

## 주의사항

결제 완료 직후 `ProductStatus`가 `TRADE_IN_PROGRESS`로 바뀌는 것은 Kafka 이벤트 기반이라 아주 짧은 지연이 있을 수 있습니다.

구매확정 후 `ProductStatus`가 `SOLD`로 바뀌는 것도 Kafka 이벤트 기반이라 약간 지연될 수 있습니다.

따라서 결제/구매확정 API 응답은 즉시 화면에 반영하고, 상품 상세는 재조회하거나 짧게 polling/refetch하는 방식을 권장합니다.
