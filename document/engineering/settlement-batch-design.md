# 판매자 정산 배치 설계

## 배경

구매확정은 구매자가 거래를 확정한 시점이고, 판매자 정산 완료는 플랫폼이 정산 금액을 확정하고 후속 이벤트를 발행한 시점입니다. 두 시점을 같은 상태로 처리하면 구매확정 직후 장애가 발생했을 때 정산 완료 여부와 이벤트 발행 여부를 분리해서 복구하기 어렵습니다.

## 상태 흐름

- 결제 승인: `REQUESTED -> PG_PENDING -> ESCROWED`
- 구매확정: `ESCROWED -> PURCHASE_CONFIRMED`
- 정산 배치 완료: `PURCHASE_CONFIRMED -> RELEASED`
- 정산 row: `PENDING -> COMPLETED` 또는 `FAILED`

## 구현 기준

- `PaymentCommandService.confirmPurchase()`는 결제를 `PURCHASE_CONFIRMED`로 변경하고 `SettlementStatus.PENDING` 정산 row만 생성합니다.
- `paymentSettledEvent`는 구매확정 시점이 아니라 정산 배치 writer에서 `COMPLETED` 처리 후 발행합니다.
- `Settlement.paymentId`는 unique로 유지해 구매확정 API 재시도 시 중복 정산 row 생성을 방지합니다.
- Batch reader는 `SettlementStatus.PENDING`만 조회합니다.
- Processor는 payment를 lock 조회하고 `PaymentStatus.PURCHASE_CONFIRMED`인지 검증합니다.
- Writer는 성공 item에 대해 payment를 `RELEASED`, settlement를 `COMPLETED`로 변경하고 outbox 이벤트를 저장합니다.
- 실패 item은 settlement를 `FAILED`로 남겨 운영자가 원인을 확인하고 별도 재처리할 수 있게 합니다.

## 수수료 계산

- 기본 플랫폼 수수료율: `0.05`
- 기본 PG 수수료율: `0.00`
- 금액 계산은 `setScale(2, RoundingMode.DOWN)` 기준입니다.
- 설정 키는 `payment.settlement-batch.platform-fee-rate`, `payment.settlement-batch.pg-fee-rate`입니다.

## 운영 설정

- `payment.settlement-batch.cron`: 정산 배치 실행 주기
- `payment.settlement-batch.chunk-size`: chunk 크기
- `spring.batch.job.enabled=false`: 애플리케이션 시작 시 Job 자동 실행 방지
- `spring.batch.jdbc.initialize-schema=always`: Spring Boot Batch JDBC auto-configuration으로 Batch 메타 테이블 자동 초기화
- `@EnableBatchProcessing`을 직접 선언하면 Boot Batch auto-configuration이 물러나 schema initialization도 동작하지 않으므로 사용하지 않습니다.

## 검증

- `PaymentCommandServiceTest`: 구매확정 시 `PURCHASE_CONFIRMED`와 `PENDING` 정산 생성 검증
- `SettlementBatchTest`: 수수료 계산, `RELEASED/COMPLETED` 전이, 실패 item 처리, outbox 발행 시점 검증
- 실행 명령: `./gradlew :payment-service:test`
