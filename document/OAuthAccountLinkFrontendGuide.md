# OAuth 계정 연결 프론트 연동 가이드

## 개요

카카오 앱에 `account_email` 권한이 없으면 백엔드는 카카오 이메일을 받을 수 없습니다.

따라서 카카오 계정이 아직 픽빗에 연결되어 있지 않은 경우, 프론트에서 사용자가 아래 중 하나를 선택하게 합니다.

- 기존 픽빗 계정에 카카오 연결
- 카카오로 새 픽빗 계정 생성

대신 사용자가 기존 픽빗 계정 비밀번호로 본인 인증을 완료하면 카카오 계정을 기존 픽빗 계정에 연결합니다.

연결 이후에는 같은 계정으로 아래 로그인 방식이 모두 가능합니다.

- 픽빗 이메일/비밀번호 로그인
- 카카오 로그인

## OAuth Redirect 분기

카카오 로그인 완료 후 백엔드는 상황에 따라 프론트로 다른 URL에 redirect합니다.

### 1. 기존 OAuth 연결 계정

이미 해당 카카오 계정이 픽빗 계정에 연결되어 있으면 기존 콜백 URL로 이동합니다.

```text
{FRONTEND_OAUTH_CALLBACK_URL}?code={exchangeCode}
```

프론트는 기존처럼 `POST /api/auth/oauth/exchange`를 호출하면 됩니다.

### 2. 이메일을 받은 신규 OAuth 사용자

카카오 계정도 처음이고, 같은 이메일의 기존 픽빗 계정도 없으면 OAuth 추가 가입 화면으로 이동합니다.

```text
{FRONTEND_OAUTH_SIGNUP_URL}?code={signupCode}
```

프론트는 기존처럼 `GET /api/auth/oauth/signup-context`, `POST /api/auth/oauth/signup`을 사용하면 됩니다.

현재 카카오 설정에서는 이메일을 받을 수 없으므로 이 케이스는 거의 발생하지 않습니다.

### 3. 카카오 이메일이 없는 신규 OAuth 사용자

카카오 계정은 처음이고 이메일을 받을 수 없으면 연결/가입 선택 화면으로 이동합니다.

```text
{FRONTEND_OAUTH_LINK_URL}?linkCode={linkCode}&signupCode={signupCode}
```

프론트는 `/oauth/link` 같은 화면을 만들고 사용자가 아래 중 하나를 선택하게 합니다.

- 기존 계정 연결: `linkCode`로 `POST /api/auth/oauth/link` 호출
- 새 계정 생성: `signupCode`로 기존 `GET /api/auth/oauth/signup-context`, `POST /api/auth/oauth/signup` 사용

### 4. 카카오 이메일과 같은 기존 계정이 있는 경우

나중에 카카오 `account_email` 권한을 얻으면 이 케이스가 동작합니다.

```text
{FRONTEND_OAUTH_LINK_URL}?code={linkCode}
```

이 경우 프론트는 기존 픽빗 계정 비밀번호만 입력받으면 됩니다.

## 추가된 환경변수

프론트 계정 연결 화면 URL입니다.

```text
FRONTEND_OAUTH_LINK_URL=https://pickbit.co.kr/oauth/link
```

## 계정 연결 화면

### URL 예시

```text
/oauth/link?code={linkCode}
```

### 화면 목적

카카오 이메일을 받을 수 없는 현재 상태에서는 연결/가입 선택 화면으로 사용합니다.

```text
카카오 계정이 아직 픽빗에 연결되어 있지 않습니다.
기존 픽빗 계정이 있다면 이메일과 비밀번호를 입력해 연결하세요.
처음이라면 새 계정을 만들어주세요.
```

## 계정 연결 컨텍스트 조회

계정 연결 화면 진입 시 `linkCode` 또는 `code`로 연결 대상 정보를 조회합니다.

```http
GET /api/auth/oauth/link-context?code={linkCode}
```

### 응답

```json
{
  "provider": "KAKAO",
  "email": "user@example.com",
  "nickname": "카카오닉네임"
}
```

카카오 이메일 권한이 없는 경우 응답의 `email`, `nickname`은 `null`일 수 있습니다.

```json
{
  "provider": "KAKAO",
  "email": null,
  "nickname": null
}
```

### 프론트 사용 방식

- `email`이 있으면 화면에 표시합니다.
- `email`이 `null`이면 사용자가 직접 픽빗 이메일을 입력하게 합니다.
- `provider`가 `KAKAO`이면 “카카오 계정 연결” 문구로 표시합니다.
- `signupCode`가 URL에 있으면 “새 계정 생성” 버튼도 보여줍니다.

## 계정 연결 완료

사용자가 기존 픽빗 계정 이메일과 비밀번호를 입력하면 아래 API를 호출합니다.

```http
POST /api/auth/oauth/link
Content-Type: application/json
```

### 요청

```json
{
  "code": "link-code",
  "email": "user@example.com",
  "password": "password123"
}
```

### 성공 응답

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 1800000,
  "refreshTokenExpiresIn": 1209600000
}
```

성공 시 백엔드는 토큰 쿠키도 함께 설정합니다.

프론트는 기존 로그인 성공 처리와 동일하게 메인 페이지 또는 이전 페이지로 이동하면 됩니다.

## 에러 처리

### 401 Unauthorized

아래 경우 발생합니다.

- `linkCode`가 만료됨
- `linkCode`가 이미 사용됨
- 기존 픽빗 계정 비밀번호가 틀림
- 입력한 이메일의 픽빗 계정이 없음
- 연결 대상 계정을 찾을 수 없음

권장 UX:

```text
인증 정보가 올바르지 않거나 만료되었습니다. 다시 카카오 로그인을 시도해주세요.
```

비밀번호 입력 직후 실패라면 아래처럼 보여줘도 됩니다.

```text
비밀번호가 올바르지 않습니다.
```

### 409 Conflict

아래 경우 발생합니다.

- 이미 해당 카카오 계정이 다른 계정에 연결됨
- 해당 픽빗 계정에 이미 같은 provider가 연결됨

권장 UX:

```text
이미 연결된 계정입니다. 다시 로그인해주세요.
```

## 전체 플로우 요약

```text
1. 사용자가 카카오 로그인 클릭
2. 백엔드 OAuth 성공 핸들러 진입
3. provider + providerId로 기존 연결 조회
4. 연결이 있으면 /oauth/callback?code=... redirect
5. 카카오 이메일이 있으면 이메일 기준으로 signup/link 분기
6. 카카오 이메일이 없으면 /oauth/link?linkCode=...&signupCode=... redirect
7. 프론트가 link-context 조회
8. 기존 계정 연결이면 사용자가 픽빗 이메일/비밀번호 입력
9. 프론트가 POST /api/auth/oauth/link 호출
10. 새 계정 생성이면 signupCode로 기존 OAuth signup API 호출
11. 성공하면 카카오 계정 연결 또는 신규 가입 후 토큰 발급
```

## 주의사항

- 현재 카카오 설정에서는 이메일을 받을 수 없습니다.
- 카카오 scope에 `account_email`, `profile_nickname`을 요청하지 않습니다.
- 카카오 계정 연결은 사용자가 입력한 픽빗 이메일/비밀번호로 인증합니다.
- 반드시 기존 픽빗 비밀번호 인증이 필요합니다.
- `linkCode`는 임시 코드라 만료될 수 있습니다.
- `linkCode`는 연결 완료 시 1회 사용 후 폐기됩니다.
- `signupCode`도 임시 코드라 만료될 수 있습니다.
