# 결제 및 거래 단계 프론트 흐름

## 1. 목적

이 문서는 경매가 끝난 뒤 프론트에서 어떤 화면과 버튼을 보여줘야 하는지 정리한 문서입니다.

경매가 종료되면 바로 거래가 완료되는 것이 아닙니다. 경매 종료는 **낙찰자가 결정된 상태**이고, 이후에는 결제와 물건 확인 단계가 이어집니다.

```text
경매 종료
-> 낙찰자 결정
-> 결제 대기
-> 결제 완료
-> 배송/전달
-> 구매자 물건 확인
-> 구매 확정
-> 거래 완료
```

---

## 2. 핵심 개념

### 경매 종료와 결제 취소는 다릅니다

경매가 끝나면 `AuctionStatus`는 `ENDED`가 됩니다. 이 상태는 경매가 취소됐다는 뜻이 아니라, **낙찰자가 결정됐다는 뜻**입니다.

이후 낙찰자가 결제를 취소하거나 결제하지 않아도 경매 자체는 `ENDED` 상태로 남습니다.

```text
경매 종료: 낙찰자 결정 완료
결제 취소: 낙찰자의 결제 의무 불이행
```

따라서 프론트에서도 결제 취소를 “경매 취소”로 표현하지 않는 것이 좋습니다.

추천 표현:

```text
결제가 취소되었습니다.
결제 기한이 만료되었습니다.
낙찰 후 결제가 완료되지 않았습니다.
```

비추천 표현:

```text
경매가 취소되었습니다.
```

---

## 3. 전체 화면 흐름

```text
1. 경매 진행
2. 경매 종료
3. 낙찰자에게 결제 대기 상품 노출
4. 결제하기
5. 결제 완료
6. 판매자 배송/전달
7. 구매자 물건 확인
8. 구매 확정
9. 거래 완료
```

---

## 4. 경매 종료 직후

경매가 종료되고 낙찰자가 있으면, 낙찰자에게 결제해야 할 상품이 생깁니다.

프론트에서는 낙찰자 마이페이지 또는 결제 페이지에서 `결제 대기 상품`을 보여줍니다.

예상 화면:

```text
[결제 대기 상품]
상품명
상품 이미지
낙찰가
판매자
결제 기한
결제하기 버튼
결제 취소 버튼
```

낙찰자가 아닌 사용자는 결제 버튼을 볼 수 없습니다.

---

## 5. 결제 대기 상품 조회

결제 목록은 하나의 API에서 쿼리 조건으로 조회합니다.

```http
GET /api/payments/me?paymentType=REQUIRED
GET /api/payments/me?status=ESCROWED
GET /api/payments/me?paymentType=HISTORY&page=0&size=20
```

응답 예시:

```json
{
  "content": [
  {
    "paymentId": 1,
    "auctionId": 10,
    "productId": 3,
    "productName": "아이폰 15",
    "productThumbnailUrl": "https://example.com/image.jpg",
    "sellerNickname": "seller1",
    "buyerNickname": "buyer1",
    "amount": 100000,
    "status": "REQUESTED",
    "paymentDeadlineAt": "2026-05-02T18:00:00",
    "paidAt": null,
    "refundedAt": null
  }
  ]
}
```

프론트 처리:

```text
paymentType=REQUIRED 조회 결과는 결제하기 버튼 표시
paymentDeadlineAt 기준으로 남은 결제 시간 표시
결제 기한이 지나면 결제 불가 상태 표시
```

---

## 6. 결제하기

낙찰자가 결제하기 버튼을 누르면 결제창 요청 정보를 조회합니다.

```http
GET /api/payments/{paymentId}/request-info
```

응답 예시:

```json
{
  "paymentId": 1,
  "pgOrderId": "payment-1-...",
  "amount": 100000,
  "orderName": "아이폰 15",
  "customerKey": "buyer-100",
  "successUrl": "https://front.example.com/payment/success",
  "failUrl": "https://front.example.com/payment/fail",
  "paymentDeadlineAt": "2026-05-02T18:00:00"
}
```

프론트 처리:

```text
결제창 요청 정보 조회
-> Toss 결제창 호출
-> 결제 성공 페이지에서 POST /api/payments/confirm 호출
-> 결제 결과 페이지 표시
```

---

## 7. 결제 성공

결제가 성공하면 상태가 결제 완료로 바뀝니다.

```text
PaymentStatus: ESCROWED
```

프론트 화면:

```text
결제 완료
판매자 배송/전달 대기 중
```

버튼 처리:

```text
결제하기 버튼 숨김
결제 취소 버튼 숨김 또는 정책에 따라 환불 요청 버튼 표시
```

---

## 8. 배송 또는 물건 전달

결제 완료 후 판매자가 물건을 배송하거나 직접 전달합니다.

상태 흐름:

```text
DELIVERY_PENDING
-> DELIVERING
-> INSPECTION_PENDING
```

프론트 화면:

```text
배송/전달 대기
배송 중
물건 확인 대기
```

구매자는 물건을 받은 뒤 확인 단계로 넘어갑니다.

---

## 9. 물건 확인 및 구매 확정

구매자가 물건을 확인하면 구매 확정을 할 수 있습니다.

```text
TradeStatus: INSPECTION_PENDING
```

프론트 버튼:

```text
구매 확정
문제 신고
환불 요청
```

구매 확정 시:

```text
TradeStatus: COMPLETED
ProductStatus: SOLD
```

프론트 화면:

```text
거래 완료
구매 확정 완료
```

---

## 10. 결제 취소

낙찰자가 결제 전에 결제를 취소할 수 있습니다.

결제 취소 시:

```text
AuctionStatus: ENDED 유지
PaymentStatus: CANCELLED
TradeStatus: PAYMENT_CANCELLED
ProductStatus: ACTIVE
```

프론트 표현:

```text
결제가 취소되었습니다.
낙찰 후 결제가 완료되지 않았습니다.
```

주의:

```text
경매가 취소된 것은 아닙니다.
경매는 이미 종료되었고, 결제 단계에서 취소된 것입니다.
```

반복 취소는 사용자 패널티 대상이 될 수 있습니다.

---

## 11. 결제 기한 만료

낙찰자가 정해진 시간 안에 결제하지 않으면 결제 기한이 만료됩니다.

```text
PaymentStatus: FAILED
ProductStatus: ACTIVE
```

프론트 표현:

```text
결제 기한이 만료되었습니다.
낙찰 후 결제가 완료되지 않았습니다.
```

패널티 안내:

```text
낙찰 후 결제를 반복적으로 취소하거나 기한 내 결제하지 않으면 경매 참여가 제한될 수 있습니다.
```

---

## 12. 결제 실패

카드 문제, PG 오류, 네트워크 문제 등으로 결제가 실패할 수 있습니다.

```text
PaymentStatus: FAILED
```

프론트 처리:

```text
결제 실패 안내
다시 결제하기 버튼 표시
```

결제 실패는 사용자의 고의가 아닐 수 있으므로 결제 취소나 기한 만료와 다르게 안내합니다.

추천 문구:

```text
결제 처리에 실패했습니다. 다시 시도해주세요.
```

---

## 13. 상태별 프론트 처리

### PaymentStatus

```text
REQUESTED: 결제 대기, 결제하기 버튼 표시
PG_PENDING: PG 결제창 진행 중
ESCROWED: 결제 완료, 에스크로 보관 중
CANCELLED: 결제 취소 표시
FAILED: 결제 실패 또는 기한 만료 표시
RELEASED: 판매자 정산 완료
REFUNDED: 환불 완료 표시
DISPUTED: 분쟁 중
```

### TradeStatus

```text
PAYMENT_PENDING: 결제 대기
PAID: 결제 완료
DELIVERY_PENDING: 배송/전달 대기
DELIVERING: 배송 중
INSPECTION_PENDING: 구매자 물건 확인 대기
COMPLETED: 거래 완료
PAYMENT_CANCELLED: 결제 취소
PAYMENT_EXPIRED: 결제 만료
REFUND_REQUESTED: 환불 요청 중
REFUNDED: 환불 완료
DISPUTED: 분쟁 중
```

### ProductStatus

```text
ACTIVE: 다시 판매/경매 가능
AUCTION_SCHEDULED: 경매 예정
IN_AUCTION: 경매 중
AUCTION_COMPLETED: 경매 종료, 결제/거래 진행 가능
INACTIVE: 비활성화
DELETED: 삭제됨
```

---

## 14. 사용자 패널티 안내

낙찰 후 결제를 취소하거나 결제 기한을 넘기면 패널티 대상이 될 수 있습니다.

패널티 대상:

```text
USER_CANCELLED
PAYMENT_EXPIRED
```

패널티 대상이 아닐 수 있는 상황:

```text
PAYMENT_FAILED
SYSTEM_ERROR
PG_ERROR
```

프론트 안내 문구 예시:

```text
낙찰 후 결제를 완료하지 않으면 경매 참여가 제한될 수 있습니다.
반복적인 결제 취소 또는 미결제는 이용 제한 사유가 될 수 있습니다.
```

---

## 15. 프론트 핵심 정리

```text
경매 종료는 낙찰자 결정 완료입니다.
결제 취소는 경매 취소가 아닙니다.
낙찰자는 결제 대기 상품을 마이페이지에서 확인합니다.
결제 완료 후에는 배송/전달 단계로 넘어갑니다.
구매자는 물건을 확인한 뒤 구매 확정합니다.
구매 확정 후 거래가 완료됩니다.
결제 취소나 미결제 반복은 패널티 대상이 될 수 있습니다.
```
