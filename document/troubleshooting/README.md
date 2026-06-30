# Troubleshooting

Pickbit 백엔드 구현과 연동 과정에서 발생한 문제를 증상, 원인, 해결, 재발 방지 기준으로 정리합니다.

## OAuth/Auth

- [카카오 KOE205 OAuth scope 오류](oauth-kakao-scope.md)
- [OAuth redirect URL 설정 누락](auth-config-placeholder.md)
- [Gateway 인증 헤더 전파 문제](gateway-auth-propagation.md)

## Auction

- [경매 동시성 및 실시간 알림 문제](auction-concurrency.md)

## Infra

- [Redis 의존 기능과 장애 영향 범위](redis-dependencies.md)

## Payment

- [Toss Payments Confirm/Webhook 처리](payment-confirm-webhook.md)
- [Spring Batch 정산 멱등성 및 실패 처리](settlement-batch-idempotency.md)

## Event

- [Outbox/Inbox 기반 중복 이벤트 처리](outbox-inbox-duplicate-event.md)

## Realtime

- [WebSocket 실시간 알림 수평 확장 문제](websocket-scale-out.md)

## 추가 예정 항목

- Docker deploy secret 누락
- Gateway route/rate limit 설정 문제
