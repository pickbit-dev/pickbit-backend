# Outbox/Inbox 기반 중복 이벤트 처리

## 증상

Kafka 기반 이벤트 처리에서는 동일 이벤트가 두 번 이상 소비될 수 있습니다.

예상 증상:

```text
회원가입 이벤트가 중복 처리되어 사용자 row 중복 생성 시도
닉네임 변경 이벤트가 중복 처리됨
결제 취소/만료 이벤트가 중복 처리되어 패널티가 여러 번 적용될 가능성
consumer 재시작 후 같은 eventId 재수신
```

## 원인

Kafka consumer는 일반적으로 at-least-once 전달을 전제로 설계해야 합니다.

중복 수신이 가능한 상황:

```text
consumer 처리 성공 후 offset commit 전에 장애 발생
consumer retry
Kafka rebalance
Debezium Outbox connector 재전송
서비스 재시작
```

따라서 이벤트 handler는 같은 `eventId`를 여러 번 받아도 결과가 깨지지 않도록 멱등하게 처리해야 합니다.

## 현재 처리

### Outbox

도메인 상태 변경과 이벤트 기록을 같은 DB 트랜잭션 안에서 처리하기 위해 `OutBoxEvent`를 사용합니다.

예:

```text
auth-service 회원가입
-> AuthAccount 저장
-> OutBoxEvent 저장
-> Debezium Outbox connector가 Kafka topic으로 발행
```

### Inbox

이벤트 소비 서비스는 처리 성공 이벤트를 `Inbox`에 기록합니다.

핵심 체크:

```java
inboxRepository.existsBySuccessEventId(eventId)
```

이미 성공 처리한 eventId면 `KafkaDuplicateEventException`으로 중복 처리를 막습니다.

처리 실패도 Inbox에 기록해 어떤 payload가 왜 실패했는지 추적할 수 있게 했습니다.

```text
recordSuccess(eventId, topic, action, aggregateId, messageBody)
recordFailure(eventId, topic, action, aggregateId, messageBody, errorMessage)
```

## 적용 예

```text
auth-service: user-service의 닉네임 변경 이벤트 소비
user-service: auth-service 회원가입 이벤트 소비
payment-service/user-service: 경매/결제 이벤트 소비
```

## 운영 주의

- 같은 eventId는 한 번만 성공 처리되어야 합니다.
- 실패 이벤트는 재처리 가능성과 재처리 불가능성을 구분해야 합니다.
- payload 파싱 실패처럼 재처리해도 성공 가능성이 낮은 이벤트는 별도 알림/모니터링 대상입니다.
- 중복 이벤트는 장애가 아니라 정상적으로 발생 가능한 상황으로 보고 handler를 멱등하게 작성해야 합니다.

## 재발 방지

- 새 Kafka consumer를 만들 때 `InboxService.isAlreadyProcessed` 체크를 먼저 수행합니다.
- 성공 처리 후 `recordSuccess`를 호출합니다.
- 실패 시 `recordFailure`에 topic/action/payload/errorMessage를 남깁니다.
- 이벤트 payload에는 추적 가능한 `eventId`를 반드시 포함합니다.
