# API Key 테스트 가이드

토큰을 매번 발급받지 않고 API를 호출하기 위한 장치입니다.
특히 부하 테스트에서 사용자 수백 명분의 JWT를 만들어 두는 부담을 없애기 위한 것입니다.

> **이 키는 사실상 인증 우회입니다.** 키를 아는 사람은 누구나 원하는 사용자로 API를 호출할 수
> 있습니다. 아래 안전장치와 운영 규칙을 반드시 지키세요.

---

## 1. 동작 방식

게이트웨이의 `AuthenticationGlobalFilter`가 요청을 받으면 순서대로 판단합니다.

1. `/api/internal/**` → 항상 403
2. 공개 경로(비로그인 조회 등) → 신원 헤더를 지우고 통과
3. `X-Api-Key` 헤더가 있으면 → **API key 인증**
4. 없으면 → **JWT를 게이트웨이에서 직접 검증**

두 경로 모두 결과적으로 같은 `X-User-*` 헤더를 다운스트림에 주입하므로, 서비스 코드는 어느
쪽으로 인증했는지 알지 못하고 알 필요도 없습니다.

## 2. 설정

| 프로퍼티 | 환경변수 | develop 기본 | deploy 기본 |
|---|---|---|---|
| `gateway.api-key.enabled` | `GATEWAY_API_KEY_ENABLED` | `true` | `false` |
| `gateway.api-key.key` | `GATEWAY_API_KEY` | (비어 있음) | (비어 있음) |
| `gateway.api-key.allow-admin-role` | `GATEWAY_API_KEY_ALLOW_ADMIN` | `true` | `false` |

**키가 비어 있으면 `enabled`가 `true`여도 비활성입니다.** 설정 실수로 인증이 통째로 열리는
일이 없도록 한 안전장치입니다.

키 생성:
```bash
openssl rand -hex 32
```

`secrets/application-develop-secret.yml` 또는 `secrets/application-deploy-secret.yml`에 넣습니다:
```yaml
GATEWAY_API_KEY: "생성한-값"
```

## 3. 사용법

| 헤더 | 필수 | 설명 |
|---|---|---|
| `X-Api-Key` | O | 설정한 키 |
| `X-Api-User-Id` | O | 어떤 사용자로 요청할지 (숫자) |
| `X-Api-Nickname` | X | 기본값 `apikey-user-{id}` |
| `X-Api-Role` | X | 기본값 `USER`. `ADMIN`은 `allow-admin-role`이 켜져 있을 때만 |
| `X-Api-Email` | X | 기본값 `apikey-user-{id}@pickbit.local` |

```bash
# 42번 사용자로 내 판매 목록 조회
curl -H "X-Api-Key: $PICKBIT_API_KEY" \
     -H "X-Api-User-Id: 42" \
     -H "X-Api-Nickname: tester42" \
     http://localhost:18080/api/products/me/selling

# 관리자로 카테고리 등록 (develop 에서만)
curl -X POST \
     -H "X-Api-Key: $PICKBIT_API_KEY" \
     -H "X-Api-User-Id: 1" \
     -H "X-Api-Role: ADMIN" \
     -H "Content-Type: application/json" \
     -d '{"name":"전자기기","description":"테스트","sortOrder":0}' \
     http://localhost:18080/api/categories

# 입찰 (부하 테스트에서 bidder 마다 X-Api-User-Id 만 바꾸면 된다)
curl -X POST \
     -H "X-Api-Key: $PICKBIT_API_KEY" \
     -H "X-Api-User-Id: 101" \
     -H "Content-Type: application/json" \
     -d '{"bidAmount":15000}' \
     http://localhost:18080/api/auctions/1/bids
```

## 4. 안전장치

구현에 들어 있는 것들입니다.

- **기본 비활성** — deploy는 명시적으로 켜야만 동작합니다
- **빈 키는 항상 비활성** — 설정 누락이 곧 개방으로 이어지지 않습니다
- **상수 시간 비교** (`MessageDigest.isEqual`) — 타이밍 공격 방지
- **ADMIN 역할 차단** — deploy에서는 키가 유출돼도 권한 상승이 불가능합니다
- **API key 헤더는 다운스트림으로 전달되지 않습니다** — 게이트웨이에서 소비하고 제거합니다
- **사용 시 로그 기록** — `API key 인증 통과 | accountId=.. | role=..`

## 5. 운영 규칙

- deploy에서는 **필요할 때만 켜고 끝나면 되돌립니다.** 부하 테스트 전후로 `.env`의
  `GATEWAY_API_KEY_ENABLED`를 토글하고 `docker compose up -d --no-deps gateway-service`
- 키가 유출됐다고 판단되면 **즉시 새 키로 교체**합니다 (재기동만 하면 이전 키는 무효)
- Grafana에서 사용 이력을 확인할 수 있습니다:
  ```logql
  {container="pickbit-deploy-gateway-service"} |= "API key 인증 통과"
  ```
- 키를 커밋하지 마세요. `secrets/`와 `*secret*.yml`은 gitignore 대상입니다

---

## 부록 — 게이트웨이 JWT 검증으로 바뀐 점

예전에는 게이트웨이가 인증된 요청마다 auth-service의 `/api/auth/validate`를 HTTP로 호출했습니다.
그런데 그 엔드포인트는 **리포지토리를 전혀 쓰지 않는 순수 JWT 파싱**이었기 때문에, 게이트웨이가
같은 시크릿으로 검증해도 기능상 달라지는 것이 없습니다. 대신 없어진 것은:

- 인증된 요청마다 발생하던 **네트워크 왕복 1회**
- auth-service의 **DB 커넥션 점유** (`@Transactional`이 붙어 있어 커넥션을 잡고 있었습니다)
- reactor-netty 기본 커넥션 풀(4코어 기준 **16개**)이라는 전체 시스템의 인증 처리량 상한

토큰 폐기(로그아웃)는 이전에도 검증 경로에서 확인하지 않았으므로 동작이 동일합니다.
즉시 폐기가 필요해지면 `GatewayJwtDecoder`에 Redis 기반 거부 목록을 추가하면 됩니다.

`gateway-service`는 이제 `JWT_SECRET`이 **반드시** 필요합니다. 없으면 기동에 실패합니다.
