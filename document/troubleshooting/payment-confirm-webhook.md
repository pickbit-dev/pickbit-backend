# Toss Payments Confirm/Webhook 처리

## 증상

결제 연동 중 아래 문제가 발생할 수 있습니다.

```text
결제 승인 confirm 실패
결제 금액 불일치
토스 웹훅 signature 검증 실패
이미 처리한 결제에 대한 중복 webhook 수신
서버 상태보다 늦게 도착한 webhook 수신
```

## 원인

결제는 프론트, 백엔드, PG 서버가 함께 관여합니다. 따라서 내부 상태와 외부 PG 이벤트가 항상 같은 순서로 도착한다고 가정할 수 없습니다.

주요 원인:

```text
paymentKey/orderId/amount 불일치
프론트 성공 redirect 이후 confirm API 호출 누락
Toss webhook secret 불일치
PG webhook 재전송
서버 confirm 처리와 webhook 도착 순서 차이
```

## 현재 처리

### Confirm API

`payment-service`는 결제 승인 시 `TossPaymentsClient.confirm`을 호출합니다.

```text
PaymentCommandService.confirm
-> ensureAmount
-> TossPaymentsClient.confirm
-> completeConfirm
```

내부 결제 금액과 프론트가 전달한 금액이 다르면 `PaymentAmountMismatchException`을 발생시킵니다.

```java
if (payment.getAmount().compareTo(amount) != 0) {
    throw new PaymentAmountMismatchException(payment.getAmount(), amount);
}
```

토스 API 오류 응답은 `TossPaymentApiException`으로 변환해 내부 예외와 분리합니다.

### Webhook Signature

토스 웹훅은 `TossPayments-Signature` 헤더와 raw body를 사용해 검증합니다.

```text
PaymentWebhookController
-> TossWebhookSignatureVerifier.verify
-> TossWebhookHandler.handle
```

서명이 없거나 일치하지 않으면 `InvalidWebhookSignatureException`을 발생시킵니다.

### Webhook 상태 처리

`TossWebhookHandler`는 webhook status에 따라 내부 결제 상태를 갱신합니다.

```text
DONE: 결제 완료 처리
CANCELED: 결제 취소 처리
EXPIRED: 결제 만료 처리
```

이미 처리된 상태에 대해 늦게 도착한 webhook은 무시합니다.

## 운영 주의

- confirm API는 결제 금액을 서버 저장 금액과 반드시 비교해야 합니다.
- webhook은 중복 또는 지연 도착할 수 있으므로 멱등성을 전제로 처리해야 합니다.
- webhook secret은 환경별로 다를 수 있으므로 deploy secret 값을 확인해야 합니다.
- Toss API 장애와 내부 검증 실패를 같은 에러로 취급하지 않습니다.

## 재발 방지

- 프론트 결제 성공 페이지에서 `paymentKey`, `orderId`, `amount`를 그대로 confirm API에 전달합니다.
- 서버는 `orderId`로 내부 결제 row를 찾고, `amount`를 내부 금액과 비교합니다.
- webhook signature 검증 실패는 401로 처리하고 body를 신뢰하지 않습니다.
- DONE/CANCELED/EXPIRED 이벤트는 현재 결제 상태를 확인한 뒤 가능한 전이만 수행합니다.
