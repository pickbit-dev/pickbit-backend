# WebSocket 실시간 알림 수평 확장 문제

## 증상

`auction-service`를 두 대 이상 실행하면 일부 사용자만 실시간 입찰 알림을 받을 수 있습니다.

예시:

```text
사용자 A는 auction-service-1에 WebSocket 연결
사용자 B는 auction-service-2에 WebSocket 연결
입찰 요청은 auction-service-1에서 처리
auction-service-1에 연결된 사용자만 알림 수신
auction-service-2에 연결된 사용자는 알림 미수신
```

## 원인

Spring WebSocket의 SimpleBroker는 인스턴스 내부 메모리에서 topic 구독자를 관리합니다.

즉, `auction-service-1`에서 `SimpMessagingTemplate.convertAndSend`를 호출해도 `auction-service-2`에 붙은 WebSocket 세션에는 메시지가 전달되지 않습니다.

단일 인스턴스에서는 문제가 보이지 않지만, 수평 확장 시 알림이 인스턴스 단위로 갈라집니다.

## 현재 처리

Redis Pub/Sub을 인스턴스 간 fan-out 브리지로 사용합니다.

### 발행

입찰 성공 후 DB commit이 완료된 뒤 Redis 채널로 이벤트를 발행합니다.

```text
AuctionRealtimeEventListener
-> @TransactionalEventListener(phase = AFTER_COMMIT)
-> stringRedisTemplate.convertAndSend("auction:ws:" + auctionId, payload)
```

DB commit 이후 발행하므로, DB에 반영되지 않은 입찰 성공 알림이 먼저 나가는 문제를 피합니다.

### 구독

모든 `auction-service` 인스턴스가 Redis channel pattern을 구독합니다.

```text
RedisPubSubConfig
-> PatternTopic("auction:ws:*")
```

수신한 메시지는 각 인스턴스의 로컬 SimpleBroker로 다시 보냅니다.

```text
WebSocketRedisSubscriber
-> SimpMessagingTemplate.convertAndSend("/topic/auctions/{auctionId}", payload)
```

이 구조에서는 어느 인스턴스에서 입찰이 처리되더라도 모든 인스턴스의 WebSocket 구독자에게 알림이 전달됩니다.

## 알림 폭주 완화

인기 경매 마감 직전에는 ACTIVE 이벤트가 짧은 시간에 많이 발생할 수 있습니다.

현재 처리:

```text
ACTIVE 이벤트: 경매별 마지막 이벤트만 100ms 단위로 debounce 전송
ENDED 이벤트: debounce 없이 즉시 전송
```

ENDED 이벤트는 최종 상태이므로 지연되거나 ACTIVE 이벤트에 덮이면 안 됩니다.

## 운영 주의

- Redis Pub/Sub은 메시지를 저장하지 않습니다. Redis 장애 순간의 알림은 유실될 수 있습니다.
- 알림 유실은 화면 갱신 문제이고, 입찰 정합성은 DB와 이벤트 로그로 별도 보장해야 합니다.
- WebSocket 재연결 시에는 REST 조회 또는 이벤트 히스토리 조회로 화면 상태를 보정해야 합니다.
- debounce 값은 `auction.ws.debounce-ms`로 조정 가능하게 유지합니다.

## 재발 방지

- WebSocket 알림을 로컬 broker에 직접 보내지 않고 Redis Pub/Sub 경유 구조를 유지합니다.
- DB 상태 변경 전에는 실시간 이벤트를 발행하지 않습니다.
- 수평 확장 테스트에서는 서로 다른 인스턴스에 연결된 구독자 모두 알림을 받는지 확인합니다.
- ENDED 이벤트는 debounce 대상에서 제외합니다.
