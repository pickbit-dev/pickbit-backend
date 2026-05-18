# WebSocket 실시간 알림 수평 확장

경매 상세 화면은 입찰이 들어올 때마다 현재가가 실시간으로 갱신된다. 이 흐름은 `auction-service`가 `SimpMessagingTemplate.convertAndSend("/topic/auctions/{id}", payload)` 로 브로드캐스트하고, 클라이언트는 STOMP 위에 올린 SockJS 세션으로 해당 topic을 구독한다. 그동안 단일 인스턴스 환경에서는 잘 동작했다.

그런데 운영에서 `auction-service`를 두 대 이상 띄우는 시점에 두 가지 문제가 동시에 떠올랐다.

- **인스턴스가 늘어나면 알림이 절반만 도착한다.** 기본 STOMP 브로커가 인스턴스 내부 메모리만 보고 있다. 입찰이 들어온 인스턴스에 연결된 클라이언트만 메시지를 받고, 다른 인스턴스에 붙은 클라이언트는 조용해진다.
- **인기 경매 마감 직전에 알림이 폭주한다.** 매 입찰마다 동기 브로드캐스트가 일어나서, 클라이언트가 보고 있는 화면이 의미 없이 깜빡거리고 서버/네트워크에도 부하가 누적된다. 사용자 입장에서 의미 있는 건 “마지막 가격” 한 줄인데 중간 스냅샷이 다 송출되고 있었다.

이 문서는 두 문제를 어떻게 같이 닫았는지에 대한 기록이다.

---

## 1. SimpleBroker는 인스턴스 내부만 본다

### 1.1 현재 구조

`auction-service`의 WebSocket 설정은 다음처럼 가장 단순한 형태다.

```java
// auction-service/src/main/java/com/pickbit/auctionservice/config/WebSocketConfig.java
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/auctions/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```

`enableSimpleBroker(...)` 는 Spring이 직접 in-memory로 토픽을 관리하는 브로커다. 가볍고 추가 인프라가 필요 없는 대신, **인스턴스 간에는 일절 공유되지 않는다**.

입찰이 들어오면 `BidProcessor` 가 `ApplicationEventPublisher` 로 도메인 이벤트를 발행하고, 같은 트랜잭션의 커밋 이후에 리스너가 브로커로 흘려보낸다.

```java
// auction-service/src/main/java/com/pickbit/auctionservice/application/event/AuctionRealtimeEventListener.java (변경 전)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void send(AuctionRealtimeEvent event) {
    messagingTemplate.convertAndSend("/topic/auctions/" + event.auctionId(), event.payload());
}
```

`AFTER_COMMIT` 으로 묶여 있어서 DB 커밋이 끝난 후에만 발행된다. 그래서 “입찰은 성공했는데 알림이 먼저 나가는” 식의 정합성 문제는 원래부터 없다. 다만 “인스턴스 A에 입찰이 떨어졌는데, 인스턴스 B에 붙은 구독자가 알림을 못 받는” 문제가 그대로 남아 있었다.

### 1.2 Redis Pub/Sub 브리지

브로커를 외부로 빼는 가장 자연스러운 길은 RabbitMQ STOMP relay 다. SimpleBroker 대신 `StompBrokerRelay` 를 설정하면 Spring 이 자동으로 RabbitMQ 와 STOMP 프레임을 주고받아 준다. 그런데 이 프로젝트에는 RabbitMQ 가 없고, 단지 알림 채널을 fan-out 하려고 새 인프라 + 새 yml + 새 운영 모니터링 항목을 추가하는 건 과했다.

대신 이미 쓰고 있는 Redis(Redisson 분산 락, ShedLock, Gateway Rate Limiter)에 **Pub/Sub 한 줄을 더 얹는** 방식을 택했다. SimpleBroker 는 그대로 두고, 인스턴스끼리는 Redis 채널로 이벤트만 fanout 한다.

```text
[입찰 in 인스턴스 A]
      └─ AFTER_COMMIT 리스너
            └─ Redis PUBLISH  (auction:ws:{id}, json)
                  │
                  ├─→ 인스턴스 A 의 Redis 구독자
                  │       └─ SimpMessagingTemplate.convertAndSend (/topic/auctions/{id})
                  │              → A에 붙은 클라이언트들
                  │
                  └─→ 인스턴스 B 의 Redis 구독자
                          └─ SimpMessagingTemplate.convertAndSend (/topic/auctions/{id})
                                 → B에 붙은 클라이언트들
```

발행 측은 더 이상 로컬 broker 를 직접 두드리지 않는다. Redis 한 군데에 던지고 끝.

```java
// auction-service/.../application/event/AuctionRealtimeEventListener.java (변경 후)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void send(AuctionRealtimeEvent event) {
    String body = objectMapper.writeValueAsString(event.payload());
    stringRedisTemplate.convertAndSend("auction:ws:" + event.auctionId(), body);
}
```

`StringRedisTemplate` 은 Redisson starter 가 끌고 오는 `spring-boot-starter-data-redis` 가 자동 등록해주므로 신규 의존성 0이다. 채널명 prefix(`auction:ws:`)는 Redisson 락(`auction:bid:lock:*`), ShedLock(`auction-service:*`) 과 겹치지 않도록 분리했다.

수신 측은 Spring Data Redis 의 `RedisMessageListenerContainer` 에 패턴 구독을 걸어 둔다.

```java
// auction-service/.../config/RedisPubSubConfig.java
@Bean
public RedisMessageListenerContainer redisMessageListenerContainer(
        RedisConnectionFactory connectionFactory,
        WebSocketRedisSubscriber subscriber) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(subscriber, new PatternTopic("auction:ws:*"));
    return container;
}
```

이렇게 두면 같은 Redis 를 바라보는 모든 `auction-service` 인스턴스가 `auction:ws:*` 채널을 함께 듣는다. 발행 인스턴스 자신도 자기 발행 메시지를 받게 되는데, 어차피 로컬 broker 송출을 분리했기 때문에 중복 처리가 되지는 않는다.

### 1.3 클라이언트는 한 줄도 안 바뀌었다

destination(`/topic/auctions/{id}`) 도, payload 형식(`AuctionBidEvent`) 도 그대로다. 프론트엔드는 이번 변경을 인지할 필요가 없다. 운영에서 인스턴스 수를 늘리는 순간 자동으로 알림이 양쪽에 도달하기만 하면 된다.

---

## 2. 매 입찰마다 동기 브로드캐스트

### 2.1 인기 경매 마감 직전이 문제다

평상시에는 초당 1~2건 입찰이라 매 입찰마다 알림을 쏴도 무리가 없다. 그런데 마감 1분 전 인기 경매에 스나이핑이 몰리면 같은 경매에 초당 수십 건 입찰이 들어온다.

```text
마감 직전 60초:
  초당 30건 × 60초 = 1,800건 입찰
  → 1,800건의 brodcast
  → 그 경매를 구독 중인 N명의 클라이언트에게 각각 1,800회 메시지
```

클라이언트 화면에서 현재가가 30Hz 로 깜빡거린다. 사람 눈에는 그냥 “마지막에 얼마였나” 만 의미가 있는데 중간값을 다 받아서 렌더링하고 있다.

### 2.2 구독자 측 디바운스

이번에는 구독 측에 디바운스를 걸어서 같은 경매의 연속 ACTIVE 이벤트를 묶었다.

```java
// auction-service/.../application/event/WebSocketRedisSubscriber.java
private final ConcurrentMap<Long, AuctionBidEvent> pending = new ConcurrentHashMap<>();

@Override
public void onMessage(Message message, byte[] pattern) {
    Long auctionId = parseAuctionId(message.getChannel());
    AuctionBidEvent event = objectMapper.readValue(message.getBody(), AuctionBidEvent.class);

    if (event.auctionStatus() == AuctionStatus.ENDED) {
        pending.remove(auctionId);
        sendLocal(auctionId, event);   // 종결은 즉시
        return;
    }
    pending.put(auctionId, event);     // ACTIVE 는 마지막 값만 유지
}

// 100ms 마다 한 번씩 flush — pending 의 모든 경매를 비우고 송출
scheduler.scheduleAtFixedRate(this::flushAll, debounceMs, debounceMs, TimeUnit.MILLISECONDS);
```

핵심은 `pending` 맵이 “경매별 마지막 상태” 만 들고 있다는 점이다. 같은 경매에 ACTIVE 이벤트가 100ms 안에 30건 들어와도 마지막 1건만 살아남는다. 100ms 후 flush 가 돌면 그 1건을 로컬 broker 로 송출한다. 사용자 화면은 100ms 윈도마다 한 번씩만 갱신되고, 본 가격은 항상 최신값이다.

이 디바운스를 발행 측에 두지 않은 이유는 인스턴스 간 순서 꼬임이다. A 인스턴스가 들고 있는 ACTIVE 가 B 인스턴스의 더 최근 ACTIVE 보다 늦게 flush 되면, 클라이언트는 “가격이 내려갔다 다시 올라가는” 이상한 흐름을 본다. 구독 측에서 인스턴스별로 디바운스하면 각 인스턴스가 받은 Redis 메시지 순서대로 마지막 상태만 유지하므로 이 문제가 안 생긴다.

### 2.3 ENDED 는 디바운스를 우회한다

종결 이벤트는 다르다. 디바운스 윈도에 묶여 100ms 늦게 가도 안 되고, 다음 ACTIVE 가 와서 덮어버려도 안 된다. 그래서 `onMessage` 안에서 상태를 보고 ENDED 면 pending 에서 해당 경매를 비우고 바로 send 한다.

```java
if (event.auctionStatus() == AuctionStatus.ENDED) {
    pending.remove(auctionId);
    sendLocal(auctionId, event);
    return;
}
```

순서로 보자면 “ACTIVE 가 pending 에 들어가 있다 → ENDED 도착 → pending 비우고 ENDED 송출” 이므로, 마지막에 들어온 ACTIVE 1건은 클라이언트에게 도달하지 않는다. 하지만 ENDED 의 payload 에 이미 `finalPrice` 가 들어 있어서 정보 손실이 없다.

### 2.4 비동기 효과는 구조 자체로 얻는다

이전 코드는 `AFTER_COMMIT` 리스너가 컨트롤러 요청 스레드에서 동기로 `SimpMessagingTemplate.convertAndSend` 를 호출하고 있었다. 브로커 큐 푸시까지 끝나야 컨트롤러가 응답을 반환했다.

지금은 발행 측이 Redis PUBLISH 한 줄(단일 명령, sub-ms)만 하고 반환한다. 실제 broker 송출은 구독 측 Redis 리스너 스레드에서 일어난다. 별도로 `@Async` 를 붙이지 않아도 발행/송출이 자연스럽게 분리됐다. 추가 스레드풀이나 큐 관리도 없다.

### 2.5 디바운스 주기는 yml 로 외부화

```yaml
# auction-service/src/main/resources/application-develop.yml
auction:
  ws:
    debounce-ms: 100
```

기본 100ms 로 시작했다. 사람 눈에 거의 무지각이고 동시에 폭주 트래픽을 30:1 수준으로 줄인다. qa/deploy 는 `${AUCTION_WS_DEBOUNCE_MS:100}` 패턴으로 환경변수에서 조정할 수 있게 두었다. 마감 카운트다운 화면에서 1~2 frame 늦어 보인다는 피드백이 들어오면 50ms 로 내리면 된다.

---

## 3. 세 가지가 같이 작동한다

이전 작업에서 입찰 트래픽 안정성을 위해 Rate Limit + Redisson 락 + `@Version` 을 깔아두었다. 이번 변경은 이 위에 “실시간 알림” 축을 하나 더 얹은 셈이다.

| 구분 | 대상 | 책임 |
|---|---|---|
| Gateway Rate Limit | 트래픽 양 | 한 사용자가 너무 자주 보내지 못하게 막는다. |
| Redisson 락 | 같은 경매 입찰 동시성 | 입찰끼리 순서대로 처리되도록 직렬화한다. |
| `@Version` | DB 행 동시성 | 입찰이 아닌 경로(스케줄러/취소)와의 교차 동시성을 막는다. |
| **Redis Pub/Sub 브리지** | **인스턴스 간 알림 전파** | **인스턴스 어디에 입찰이 떨어져도 모든 구독자에게 도달한다.** |
| **구독자 측 디바운스** | **클라이언트 렌더링/네트워크** | **같은 경매의 연속 ACTIVE 를 100ms 윈도로 묶어 송출 빈도를 낮춘다.** |

겹쳐서 작동한다. Rate Limit 이 못 막은 정상 트래픽은 락이 직렬화하고, 락이 못 막은 교차 경로는 `@Version` 이 잡는다. 그리고 그 모든 흐름의 결과는 Redis Pub/Sub 을 통해 어느 인스턴스에 붙은 클라이언트든 한 번씩만, 의미 있는 마지막 상태로 도달한다.

---

## 4. 운영 시 주의

### 4.1 Redis 가용성

기존에도 Redisson 락 때문에 Redis 가 떠 있어야 했지만, 이번 변경으로 “실시간 알림” 도 Redis 의존성에 들어갔다. Redis 가 잠시 끊기면 알림 메시지는 그 순간 사라진다(Pub/Sub 은 영속성/QoS 가 없다). 다만 입찰 정합성 자체는 DB + Outbox 로 별도 보장되므로 영향은 “화면이 안 바뀐다” 까지다. 클라이언트가 재구독하거나 새로고침하면 복구된다.

### 4.2 인스턴스 종료 시점 메시지 유실

`WebSocketRedisSubscriber` 는 종료 시 `@PreDestroy` 에서 scheduler 를 멈추고 pending 을 마지막으로 비운다. 그 사이에 새 onMessage 가 호출되면 그 메시지는 송출되지 않을 수 있다. 영향이 작아서 별도 처리는 하지 않았다. 무중단 배포에서 일반적인 graceful shutdown 흐름으로 받는다.

### 4.3 디바운스 윈도와 카운트다운

마감 직전 1~2초 동안은 100ms 늦게 보이는 것도 사용자 입장에서 의미가 클 수 있다. 출시 후 실제 사용자 피드백을 보고 50ms 또는 더 짧게 조정할 수 있도록 yml 키로 분리해 두었다. 코드 재빌드 없이 환경변수만 바꾸면 된다.

### 4.4 RabbitMQ 로의 마이그레이션 여지

지금은 Pub/Sub 한 채널 패턴만 쓰고 있어서 Redis 만으로 충분하다. 만약 나중에 “알림 영속성”, “재구독 시 백로그 재생” 같은 요구가 들어오면 그때 SimpleBroker + Redis 브리지를 `StompBrokerRelay` + RabbitMQ 로 갈아끼우면 된다. 그 때도 `AuctionRealtimeEventListener` 와 클라이언트는 손대지 않아도 되도록 destination 추상화는 유지해 두었다.
