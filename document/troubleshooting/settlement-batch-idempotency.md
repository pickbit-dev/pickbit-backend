# Spring Batch 정산 멱등성 및 실패 처리

## 증상

구매확정 이후 정산 배치가 중간에 실패하거나 같은 정산 대상이 중복 처리되면 판매자 정산 이벤트가 중복 발행될 수 있습니다.

## 원인

- 구매확정과 정산완료를 같은 트랜잭션/상태로 처리하면 장애 시점별 복구 기준이 모호합니다.
- Batch 재실행은 정상 운영 시나리오이므로 writer와 이벤트 발행 시점이 멱등하지 않으면 중복 정산으로 이어질 수 있습니다.
- payment와 settlement 상태가 불일치하면 어떤 row를 재처리해야 하는지 판단하기 어렵습니다.

## 해결

- 구매확정 API는 `PaymentStatus.PURCHASE_CONFIRMED`와 `SettlementStatus.PENDING`까지만 처리합니다.
- 정산 완료는 Spring Batch writer에서만 수행합니다.
- reader는 `PENDING` settlement만 읽습니다.
- processor는 payment 상태가 `PURCHASE_CONFIRMED`가 아니면 성공 처리하지 않고 settlement를 `FAILED` 대상으로 반환합니다.
- writer는 성공 item에서만 `PaymentStatus.RELEASED`, `SettlementStatus.COMPLETED`, `paymentSettledEvent`를 함께 처리합니다.
- `Settlement.paymentId` unique 제약으로 구매확정 재요청이 중복 정산 row를 만들지 못하게 합니다.

## 재발 방지

- `FAILED` settlement는 자동 재시도하지 않고 원인 확인 후 별도 재처리 대상으로 둡니다.
- outbox 이벤트는 정산 완료 트랜잭션 안에서 저장해 payment/settlement 상태와 이벤트 저장이 함께 성공하도록 유지합니다.
- Batch 메타 테이블 자동 생성은 `spring.batch.jdbc.initialize-schema=always`와 `spring-boot-starter-batch-jdbc`를 사용합니다.
- `@EnableBatchProcessing`을 직접 선언하면 Boot Batch JDBC auto-configuration이 back off 되어 메타 테이블 초기화가 동작하지 않습니다.
- 운영에서는 `PENDING` 장기 체류 건과 `FAILED` 건수를 모니터링합니다.
- 정산 수수료율 변경은 환경변수 `SETTLEMENT_PLATFORM_FEE_RATE`, `SETTLEMENT_PG_FEE_RATE`로 반영하고 변경 시 테스트 배치로 검증합니다.
