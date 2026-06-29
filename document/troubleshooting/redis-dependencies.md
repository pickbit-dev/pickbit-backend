# Redis 의존 기능과 장애 영향 범위

## 증상

Redis가 내려가거나 연결이 불안정하면 여러 서비스 기능이 동시에 영향을 받을 수 있습니다.

대표 영향:

```text
auth-service: refresh token 검증 실패, OAuth 임시 code 조회 실패
auction-service: 입찰 락 실패, 스케줄러 락 실패, 실시간 알림 fan-out 실패, 캐시 실패
gateway-service: rate limit 처리 실패
payment-service: 스케줄러 락 실패
```

## 원인

Pickbit에서 Redis는 단순 캐시가 아니라 여러 핵심 경로의 공통 인프라입니다.

현재 Redis 사용처:

| 서비스 | 사용처 | 역할 |
| --- | --- | --- |
| `auth-service` | `RefreshTokenRedisRepository` | refresh token 저장/검증 |
| `auth-service` | `OAuthExchangeCodeRepository` | OAuth 로그인 완료 code 저장 |
| `auth-service` | `OAuthSignupCodeRepository` | OAuth 신규 가입 code 저장 |
| `auth-service` | `OAuthLinkCodeRepository` | OAuth 기존 계정 연결 code 저장 |
| `auction-service` | Redisson `auction:bid:lock:{auctionId}` | 같은 경매 입찰 직렬화 |
| `auction-service` | ShedLock | 경매 스케줄러 중복 실행 방지 |
| `auction-service` | Redis Pub/Sub `auction:ws:*` | WebSocket 실시간 알림 fan-out |
| `auction-service` | Redis cache | 경매 상세 캐시 |
| `gateway-service` | Redis `RequestRateLimiter` | 사용자별 입찰 API rate limit |
| `payment-service` | ShedLock | 결제 스케줄러 중복 실행 방지 |

## 현재 처리

Redis key prefix를 기능별로 분리해 충돌 가능성을 줄였습니다.

```text
refresh token: auth:refresh:*
OAuth exchange code: oauth:exchange:*
OAuth signup code: oauth:signup:*
OAuth link code: oauth:link:*
입찰 락: auction:bid:lock:*
WebSocket Pub/Sub: auction:ws:*
ShedLock: auction-service:*, payment-service:*
```

입찰 락은 lease time을 고정하지 않고 Redisson watchdog을 사용합니다. 정상 처리 중에는 TTL이 자동 연장되고, 서버 장애 시에는 watchdog이 멈추면서 락이 만료됩니다.

OAuth 관련 임시 code와 refresh token은 TTL을 두어 만료되도록 관리합니다.

## 운영 주의

Redis 장애 시 영향은 기능별로 다릅니다.

```text
인증: refresh token 재발급 실패 가능
OAuth: callback 이후 code 교환/가입/연결 실패 가능
입찰: 같은 경매 입찰 직렬화 실패 또는 입찰 API 실패 가능
실시간 알림: DB 정합성은 유지되지만 WebSocket 알림 누락 가능
Gateway: rate limit 필터 동작 실패 가능
스케줄러: 중복 실행 방지 실패 가능
```

Redis는 develop/deploy 환경에서 host/port가 다릅니다.

```text
develop host port: localhost:16379
deploy: Docker 내부 네트워크 redis:6379
```

## 재발 방지

- 새 Redis 사용처를 추가할 때 key prefix를 명시합니다.
- Redis 장애 시 데이터 정합성에 직접 영향을 주는 기능과 UX에만 영향을 주는 기능을 구분합니다.
- develop/deploy 프로파일의 Redis host/port 설정을 배포 전 확인합니다.
- Redis Pub/Sub은 메시지 영속성이 없으므로, 중요한 상태는 반드시 DB 또는 이벤트 로그로 보정할 수 있게 둡니다.
