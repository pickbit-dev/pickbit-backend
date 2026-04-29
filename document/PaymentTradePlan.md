# 결제 및 거래 단계 기획

## 개요

경매가 종료되면 경매 자체는 끝난 것으로 본다. 이후 흐름은 경매 취소가 아니라 **낙찰자의 결제 및 거래 이행 단계**로 분리한다.

즉, 결제 취소나 미결제는 경매가 취소된 것이 아니라 **낙찰자가 결제 의무를 이행하지 않은 상태**로 처리한다.

```text
경매 종료
-> 낙찰자 결정
-> 결제 대기 상품 생성
-> 결제
-> 물건 전달/배송
-> 구매자 물건 확인
-> 구매 확정
-> 거래 완료
```

---

## 기본 원칙

```text
Auction은 낙찰자 결정까지만 책임진다.
Payment는 결제 요청/승인/취소/실패를 책임진다.
Trade는 결제 이후 물건 확인/구매 확정/분쟁을 책임진다.
Product는 상품이 다시 판매 가능한지, 거래 중인지, 판매 완료인지 표현한다.
User는 낙찰 후 미결제/취소 반복에 대한 패널티를 관리한다.
```

---

## 전체 흐름

### 1. 경매 진행

```text
AuctionStatus: ACTIVE
ProductStatus: IN_AUCTION
```

사용자는 경매에 입찰할 수 있다.

---

### 2. 경매 종료 및 낙찰자 결정

경매 종료 시간이 되었거나 즉시구매가 발생하면 경매는 종료된다.

```text
AuctionStatus: ENDED
```

이 시점에 경매 자체는 끝난다. 낙찰자가 있다면 결제해야 할 상품이 생성된다.

```text
PaymentStatus: PAYMENT_REQUIRED
TradeStatus: PAYMENT_PENDING
ProductStatus: TRADE_IN_PROGRESS
```

프론트에서는 낙찰자에게 `결제 대기 상품`을 보여준다.

---

### 3. 결제 대기 상품 노출

낙찰자는 마이페이지 또는 결제 페이지에서 결제해야 할 상품을 확인한다.

예상 API:

```http
GET /payments/me/required
```

또는 추후 trade 도메인을 분리한다면:

```http
GET /trades/me/pending-payments
```

응답 예시:

```json
[
  {
    "auctionId": 10,
    "productId": 3,
    "productName": "아이폰 15",
    "thumbnailUrl": "https://example.com/image.jpg",
    "sellerNickname": "seller1",
    "winnerNickname": "buyer1",
    "amount": 100000,
    "paymentStatus": "PAYMENT_REQUIRED",
    "paymentDueAt": "2026-05-02T18:00:00"
  }
]
```

프론트 표시 항목:

```text
상품명
상품 이미지
낙찰가
판매자
결제 기한
결제하기 버튼
결제 취소 버튼
```

---

### 4. 결제 요청

낙찰자가 결제하기 버튼을 누르면 결제 요청을 생성한다.

```http
POST /payments
```

요청 예시:

```json
{
  "auctionId": 10,
  "provider": "TOSS"
}
```

또는:

```json
{
  "auctionId": 10,
  "provider": "NAVER_PAY"
}
```

결제 제공자는 우선 다음을 고려한다.

```text
TOSS
NAVER_PAY
```

결제 요청 후 상태:

```text
PaymentStatus: REQUESTED
TradeStatus: PAYMENT_PENDING
```

---

### 5. 결제 승인

토스 또는 네이버페이 결제 승인이 완료되면 `payment-service`가 승인 결과를 저장한다.

```text
PaymentStatus: APPROVED
TradeStatus: PAID
ProductStatus: TRADE_IN_PROGRESS
```

이후 판매자는 물건을 전달하거나 배송해야 한다.

---

### 6. 배송 또는 물건 전달

결제 완료 후 판매자는 물건을 전달한다.

```text
TradeStatus: DELIVERY_PENDING
-> DELIVERING
```

배송 완료 또는 직거래 전달 완료 후 구매자 확인 단계로 넘어간다.

```text
TradeStatus: INSPECTION_PENDING
```

---

### 7. 구매자 물건 확인

구매자는 물건을 확인한 뒤 구매 확정을 한다.

```text
TradeStatus: INSPECTION_PENDING
-> COMPLETED
```

구매 확정 후 상품은 최종 판매 완료 상태가 된다.

```text
ProductStatus: SOLD
```

---

## 결제 취소 및 미결제

### 결제 전 취소

낙찰자가 결제 전에 취소하면 경매 자체는 취소하지 않는다.

```text
AuctionStatus: ENDED
PaymentStatus: CANCELLED
TradeStatus: PAYMENT_CANCELLED
ProductStatus: ACTIVE
```

상품은 다시 판매 또는 재경매 가능한 상태로 되돌릴 수 있다.

이 경우 낙찰자에게 패널티를 줄 수 있다.

---

### 결제 기한 만료

낙찰자가 정해진 시간 안에 결제하지 않으면 결제 만료 처리한다.

```text
PaymentStatus: EXPIRED
TradeStatus: PAYMENT_EXPIRED
ProductStatus: ACTIVE
```

예상 정책:

```text
낙찰 후 24시간 내 결제
기한 초과 시 결제 만료
상품은 다시 ACTIVE
낙찰자에게 패널티 기록
```

---

### 결제 실패

카드 문제, PG 오류, 네트워크 문제 등으로 결제가 실패할 수 있다.

```text
PaymentStatus: FAILED
```

결제 실패는 사용자 고의가 아닐 수 있으므로 바로 패널티를 주지는 않는다.

패널티 대상은 주로 다음 케이스로 본다.

```text
USER_CANCELLED
PAYMENT_EXPIRED
```

---

## 상태 정의

### AuctionStatus

경매는 낙찰자 결정까지만 책임진다.

```text
SCHEDULED: 경매 예정
ACTIVE: 경매 진행 중
ENDED: 경매 종료, 낙찰자 결정 완료
CANCELLED: 경매 시작 전 판매자 취소
```

결제 취소가 발생해도 `AuctionStatus`는 `ENDED`를 유지한다.

---

### PaymentStatus

결제 자체의 상태를 표현한다.

```text
PAYMENT_REQUIRED: 낙찰 후 결제 필요
REQUESTED: 결제 요청 생성
APPROVED: 결제 승인 완료
CANCELLED: 사용자가 결제 취소
FAILED: 결제 실패
EXPIRED: 결제 기한 만료
REFUNDED: 환불 완료
```

---

### TradeStatus

결제 이후 거래 이행 상태를 표현한다.

```text
PAYMENT_PENDING: 결제 대기
PAID: 결제 완료
DELIVERY_PENDING: 판매자 배송/전달 대기
DELIVERING: 배송 중
INSPECTION_PENDING: 구매자 물건 확인 대기
COMPLETED: 구매 확정 및 거래 완료
PAYMENT_CANCELLED: 결제 단계에서 취소
PAYMENT_EXPIRED: 결제 기한 만료
REFUND_REQUESTED: 환불 요청
REFUNDED: 환불 완료
DISPUTED: 분쟁
```

---

### ProductStatus

상품은 세부 결제 상태보다 판매 가능 여부 중심으로 관리한다.

```text
ACTIVE: 판매/경매 등록 가능
AUCTION_SCHEDULED: 경매 예정
IN_AUCTION: 경매 진행 중
TRADE_IN_PROGRESS: 낙찰 후 결제/배송/확인 진행 중
SOLD: 구매 확정 완료
INACTIVE: 비활성
DELETED: 삭제
```

---

## 이벤트 흐름

### 경매 종료 이벤트

경매가 종료되고 낙찰자가 결정되면 이벤트를 발행한다.

```text
auction.ended
```

payload 예시:

```json
{
  "auctionId": 10,
  "productId": 3,
  "winnerNickname": "buyer1",
  "sellerNickname": "seller1",
  "finalPrice": 100000,
  "endedAt": "2026-05-01T18:00:00"
}
```

`payment-service`는 이 이벤트를 소비해서 결제 대기 정보를 만든다.

```text
PaymentStatus: PAYMENT_REQUIRED
TradeStatus: PAYMENT_PENDING
```

---

### 결제 승인 이벤트

```text
payment.approved
```

payload 예시:

```json
{
  "paymentId": 1,
  "auctionId": 10,
  "productId": 3,
  "winnerNickname": "buyer1",
  "amount": 100000,
  "provider": "TOSS"
}
```

처리 결과:

```text
PaymentStatus: APPROVED
TradeStatus: PAID
ProductStatus: TRADE_IN_PROGRESS
```

---

### 결제 취소 이벤트

```text
payment.cancelled
```

payload 예시:

```json
{
  "paymentId": 1,
  "auctionId": 10,
  "productId": 3,
  "winnerNickname": "buyer1",
  "reason": "USER_CANCELLED",
  "provider": "TOSS"
}
```

처리 결과:

```text
AuctionStatus: ENDED 유지
PaymentStatus: CANCELLED
TradeStatus: PAYMENT_CANCELLED
ProductStatus: ACTIVE
user.penalty.requested 이벤트 발행
```

---

### 결제 기한 만료 이벤트

```text
payment.expired
```

처리 결과:

```text
AuctionStatus: ENDED 유지
PaymentStatus: EXPIRED
TradeStatus: PAYMENT_EXPIRED
ProductStatus: ACTIVE
user.penalty.requested 이벤트 발행
```

---

## 사용자 패널티 정책

낙찰 후 결제를 반복적으로 취소하거나 방치하면 다른 사용자와 판매자에게 피해가 간다. 따라서 결제 취소/미결제는 사용자 신뢰도에 반영한다.

패널티 대상:

```text
USER_CANCELLED
PAYMENT_EXPIRED
```

패널티 비대상 또는 약한 대상:

```text
PAYMENT_FAILED
SYSTEM_ERROR
PG_ERROR
```

예상 정책:

```text
1회: 경고
2회: 7일 경매 참여 제한
3회: 30일 경매 참여 제한
반복 악용: 관리자 검토 또는 영구 제한
```

패널티 이벤트 예시:

```text
user.penalty.requested
```

payload 예시:

```json
{
  "userNickname": "buyer1",
  "auctionId": 10,
  "productId": 3,
  "reason": "AUCTION_PAYMENT_CANCELLED",
  "amount": 100000
}
```

---

## 프론트 화면 흐름

### 낙찰자 마이페이지

```text
결제 대기 상품 목록
결제 기한
결제하기 버튼
결제 취소 버튼
```

---

### 결제 완료 후

```text
결제 완료 상태 표시
판매자 배송/전달 대기 표시
```

---

### 배송/전달 후

```text
물건 확인 대기 상태 표시
구매 확정 버튼 표시
문제 신고 또는 환불 요청 버튼 표시
```

---

### 구매 확정 후

```text
거래 완료 표시
상품 SOLD 처리
```

---

## 구현 우선순위

### 1차

```text
Payment 도메인 추가
PaymentStatus 정의
낙찰 후 결제 대기 상품 생성
GET /payments/me/required
POST /payments
TOSS/NAVER_PAY provider 구분
```

### 2차

```text
결제 승인 callback/confirm 처리
payment.approved 이벤트 발행
ProductStatus.TRADE_IN_PROGRESS 적용
```

### 3차

```text
결제 취소/기한 만료 처리
user.penalty.requested 이벤트 발행
ProductStatus.ACTIVE 복구
```

### 4차

```text
배송/전달 상태
구매자 물건 확인
구매 확정
ProductStatus.SOLD 적용
```

### 5차

```text
환불/분쟁
패널티 정책 고도화
관리자 검토 기능
```

---

## 핵심 정리

```text
경매 종료는 경매 취소가 아니다.
경매 종료는 낙찰자 결정 완료를 의미한다.

결제 취소는 경매 취소가 아니라 낙찰자의 결제 의무 불이행이다.

결제해야 할 상품은 payment/trade 쪽에서 관리한다.

결제 취소나 미결제 반복은 user-service에서 패널티로 관리한다.

상품은 결제/거래 진행 중에는 TRADE_IN_PROGRESS,
구매 확정 후에는 SOLD 상태가 된다.
```
