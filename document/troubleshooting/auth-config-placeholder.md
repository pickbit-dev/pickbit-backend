# OAuth Redirect URL 설정 누락

## 증상

auth-service 실행 중 `OAuth2AuthenticationSuccessHandler` 빈 생성에 실패했습니다.

```text
Could not resolve placeholder 'FRONTEND_OAUTH_LINK_URL'
```

## 원인

OAuth 계정 연결 화면 redirect URL 설정이 추가됐지만, 실행 환경의 secret 또는 환경변수에 `FRONTEND_OAUTH_LINK_URL` 값이 없었습니다.

문제가 된 설정:

```yaml
frontend:
  oauth-link-url: ${FRONTEND_OAUTH_LINK_URL}
```

## 해결

기본값을 추가해 설정값이 없어도 서비스가 기동되도록 했습니다.

```yaml
frontend:
  oauth-link-url: ${FRONTEND_OAUTH_LINK_URL:https://pickbit.co.kr/oauth/link}
```

파일:

```text
auth-service/src/main/resources/application.yml
```

## 운영 권장 설정

배포 환경에서는 secret 또는 환경변수에 명시적으로 값을 넣는 것을 권장합니다.

```yaml
FRONTEND_OAUTH_LINK_URL: https://pickbit.co.kr/oauth/link
```

로컬 프론트로 redirect해야 하는 개발 환경에서는 아래처럼 override할 수 있습니다.

```yaml
FRONTEND_OAUTH_LINK_URL: http://localhost:3000/oauth/link
```

## 재발 방지

- 새 환경변수를 추가하면 `application-secret.example.yml`에도 함께 추가합니다.
- 로컬 기본값이 필요한 설정은 `${KEY:default}` 형태를 사용합니다.
- 배포 전 secret 파일에 필수 OAuth redirect URL이 모두 들어있는지 확인합니다.
