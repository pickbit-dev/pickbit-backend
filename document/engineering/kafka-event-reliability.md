# Kafka 이벤트 신뢰성 — 재시도, 재처리, 순서

> 정산 조회 API 와 정산 재시도에 대해서는 [settlement.md](./settlement.md) 를 참고하세요.

## 무엇이 문제였나

이벤트가 조용히 사라지는 경로가 있었습니다.

```
핸들러 실패 -> recordFailure 로 inbox 에 success=false 기록 -> KafkaSyncException
  -> DefaultErrorHandler(FixedBackOff(3000ms, 3회)) -> 약 9초 동안 4번 시도
  -> 재시도 소진 -> 기본 recoverer 가 로그만 찍음 -> 오프셋 전진
  -> inbox 의 실패 행을 읽는 코드가 어디에도 없음
```

즉 **9초 안에 4번 실패하면 이벤트는 영구히 유실**됐고, 인박스 실패 행은 아무도 보지 않는
부검 기록이었습니다. DB 재시작이나 순간적인 커넥션 풀 고갈이면 9초는 그냥 지나갑니다.

특히 `payment-service` 가 `Auction-topic` 의 WON 을 받아 결제를 생성하는 경로가 여기 걸리면,
**경매는 끝나고 낙찰자는 정해졌는데 결제 요청이 없는 상태**가 되고 아무도 모릅니다.

## 지금 구조

### 1단계 — 인라인 재시도 (파티션을 잠깐 붙잡음)

```java
ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
backOff.setMaxInterval(20_000L);
backOff.setMaxElapsedTime(60_000L);   // 약 1분
```

순간적인 장애는 여기서 끝납니다. **1분으로 제한한 이유**는 이 재시도가 해당 파티션을
붙잡고 있기 때문입니다. 더 길게 잡으면 뒤에 쌓인 다른 이벤트가 전부 막힙니다.

재시도해도 결과가 달라지지 않는 예외는 즉시 실패 처리합니다.
- `KafkaDuplicateEventException` — 이미 처리한 이벤트
- `KafkaInvalidMessageException` — 페이로드가 깨졌거나 필수 값 누락

### 2단계 — 소진 시 인박스에 확실히 남김

`ExhaustedRetryRecoverer` 가 실패를 인박스에 기록합니다. 지정하지 않으면 Spring Kafka 의
기본 recoverer 가 로그만 찍고 오프셋을 넘깁니다 — 그게 유실의 원인이었습니다.

**오프셋은 그대로 넘깁니다.** 붙잡고 있으면 그 파티션의 뒤 이벤트가 전부 막히기 때문입니다.
말씀하신 원칙 그대로 — 인프라 문제는 붙잡고 재시도, 그래도 안 되면 실패로 기록하고 넘김.

### 3단계 — 인박스 재처리 스케줄러 (파티션을 붙잡지 않음)

`InboxRetryScheduler` 가 2분마다 성공 기록이 없는 실패 행을 찾아 다시 처리합니다.
인박스에 **원본 페이로드가 그대로 저장**되어 있어 별도 저장소 없이 재처리가 됩니다.

- 실패할수록 다음 시도를 뒤로 미룹니다 (기본 60초 × 2^n, 최대 1시간)
- 기본 10회까지 시도하고, 넘으면 더 시도하지 않고 사람이 봐야 하는 상태로 남습니다
- 리스너와 **같은 진입점**(`InboxEventHandler.handle`)을 타므로 재처리가 원래 처리와 정확히 같은 일을 합니다

## 순서 역전과 버전

평소에는 Kafka 가 순서를 보장합니다. 메시지 키가 `aggregate_id` 라서
(`transforms.outbox.table.field.event.key`) 같은 aggregate 의 이벤트는 같은 파티션으로 가고,
파티션당 소비자 스레드는 하나입니다. 리스너 동시성을 3으로 올린 것도 이 보장을 깨지 않습니다.

**그런데 재처리 스케줄러가 그 보장을 깹니다.** 실패한 이벤트를 몇 분 뒤에 처리하면
그 사이 후속 이벤트가 이미 반영됐을 수 있습니다.

그래서 이벤트에 버전을 싣습니다. 새 시퀀스를 만들 필요는 없었습니다 —
**아웃박스 행의 auto-increment `id` 가 이미 단조 증가**하므로 그대로 씁니다.

```
"transforms.outbox.table.fields.additional.placement":
    "event_id:header:event_id,event_type:header:action,id:header:event_version"
```

핸들러는 처리 전에 확인합니다.

```java
if (inboxService.isStale(TOPIC, aggregateId, eventVersion)) {
    throw new KafkaDuplicateEventException(...);   // 이미 더 최신 이벤트를 반영했다
}
```

`inbox` 에 `(topic, aggregate_id, event_version)` 인덱스가 있어 이 조회는 가볍습니다.

> **커넥터를 다시 등록해야 헤더가 붙습니다.** 갱신 전에 발행된 메시지에는 헤더가 없으므로
> 코드는 `event_version` 이 없으면 순서 검사를 건너뛰도록 되어 있습니다(하위 호환).

## 오프셋 커밋

`enable.auto.commit` 을 설정하지 않아 Spring Kafka 가 `false` 로 강제하고 컨테이너가 직접
커밋합니다. `AckMode` 는 기본값 `BATCH` 입니다. 순서는 이렇습니다.

```
핸들러 트랜잭션 커밋 (inbox 행 포함) -> 리스너 정상 반환 -> 컨테이너가 오프셋 커밋
```

인박스 저장과 오프셋 커밋은 서로 다른 시스템이라 원자적이지 않습니다. 하지만 **인박스가 먼저**
커밋되므로 그 사이에 죽으면 이벤트가 재전달되고 `isAlreadyProcessed` 가 걸러냅니다.
at-least-once + 멱등 소비자 조합이며, 반대 순서(오프셋 먼저)였다면 유실이 났을 겁니다.

## 운영

### 재처리 대기 중인 이벤트 확인

```sql
SELECT topic, action, COUNT(*), MIN(created_date), MAX(attempt_count)
FROM inbox
WHERE success = false
  AND NOT EXISTS (SELECT 1 FROM inbox s WHERE s.success_event_id = inbox.event_id)
GROUP BY topic, action;
```

### 재시도 상한을 넘어 방치된 이벤트 (사람이 봐야 함)

```sql
SELECT id, topic, action, aggregate_id, attempt_count, error_message
FROM inbox
WHERE success = false AND attempt_count >= 10;
```

### 로그

```logql
{container=~"pickbit-deploy-.*-service"} |= "인라인 재시도 소진"
{container=~"pickbit-deploy-.*-service"} |= "인박스 재처리"
```

### 설정

| 프로퍼티 | 기본값 | 설명 |
|---|---|---|
| `inbox.retry.enabled` | `true` | 재처리 스케줄러 사용 여부 |
| `inbox.retry.cron` | `0 */2 * * * *` | 재처리 주기 |
| `inbox.retry.max-attempts` | `10` | 최대 재처리 횟수 |
| `inbox.retry.base-backoff-seconds` | `60` | 실패 시 대기 시간 (지수 증가) |
| `inbox.retry.max-backoff-seconds` | `3600` | 백오프 상한 |

## 남은 한계

- **재처리 스케줄러에 분산 락이 없습니다.** 인스턴스가 여러 개가 되면 같은 이벤트를 동시에
  재처리할 수 있습니다. 핸들러가 멱등이라 결과는 같지만, 확장 시 ShedLock 을 붙이는 편이 낫습니다.
- 재시도 상한(10회)을 넘긴 이벤트는 **자동으로 알림이 가지 않습니다.** 위 쿼리나 Grafana 로
  주기적으로 확인해야 합니다. `library` 에 Slack 클라이언트가 이미 있으므로 연결할 수 있습니다.
