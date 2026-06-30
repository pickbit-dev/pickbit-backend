# Pickbit Documentation

Pickbit 백엔드 문서는 목적별로 분리합니다.

## Frontend Integration

프론트엔드 연동에 필요한 API 흐름, 화면 처리, 에러 UX를 정리합니다.

- [상품 등록부터 경매까지](frontend/auction-flow.md)
- [경매 실시간 이벤트](frontend/auction-realtime-flow.md)
- [OAuth 로그인/가입/계정 연결](frontend/oauth-flow.md)
- [결제 및 거래 단계](frontend/payment-trade-flow.md)
- [프론트 결제/거래 테스트](frontend/payment-testing-guide.md)

## Engineering Notes

백엔드 설계 의도, 문제 해결 과정, 트레이드오프, 검증 결과를 정리합니다.

- [입찰 트래픽 안정성](engineering/bid-traffic-safety.md)
- [WebSocket 실시간 알림 수평 확장](engineering/websocket-scale-out.md)
- [입찰 API 부하 테스트 분석](engineering/auction-bid-load-test-analysis.md)
- [Gatling 부하 테스트 실행 가이드](engineering/load-test-guide.md)
- [Auth Service 설계](engineering/auth-service-design.md)
- [결제 및 거래 단계 설계](engineering/payment-trade-design.md)
- [판매자 정산 배치 설계](engineering/settlement-batch-design.md)
- [공통 라이브러리 개발 가이드](engineering/library-development.md)

## Operations

개발/배포 환경, 운영 명령, 테스트 실행 명령을 정리합니다.

- [Docker 환경 가이드](operations/docker-environment.md)
- [입찰 부하 테스트 명령어](operations/bid-load-test-commands.md)

## Troubleshooting

실제 구현/연동 중 발생한 문제와 해결 방법을 증상-원인-해결 형식으로 정리합니다.

- [Troubleshooting 인덱스](troubleshooting/README.md)
- [카카오 KOE205 OAuth scope 오류](troubleshooting/oauth-kakao-scope.md)
- [OAuth redirect URL 설정 누락](troubleshooting/auth-config-placeholder.md)
- [경매 동시성 및 실시간 알림 문제](troubleshooting/auction-concurrency.md)
- [Redis 의존 기능과 장애 영향 범위](troubleshooting/redis-dependencies.md)
- [Toss Payments Confirm/Webhook 처리](troubleshooting/payment-confirm-webhook.md)
- [Spring Batch 정산 멱등성 및 실패 처리](troubleshooting/settlement-batch-idempotency.md)
- [Gateway 인증 헤더 전파 문제](troubleshooting/gateway-auth-propagation.md)
- [Outbox/Inbox 기반 중복 이벤트 처리](troubleshooting/outbox-inbox-duplicate-event.md)
- [WebSocket 실시간 알림 수평 확장 문제](troubleshooting/websocket-scale-out.md)

## Portfolio

- [Pickbit Backend Portfolio](PickbitPortfolio.html)
