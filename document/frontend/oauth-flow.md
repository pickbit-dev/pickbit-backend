# OAuth 프론트 연동 가이드

이 문서는 프론트엔드에서 자체 로그인과 OAuth 로그인을 함께 연동할 때 필요한 흐름을 정리합니다.

현재 카카오 앱은 `account_email` 권한이 없으므로 백엔드가 카카오 이메일을 받을 수 없습니다. 따라서 연결되지 않은 카카오 계정은 프론트에서 “기존 계정 연결” 또는 “새 계정 생성”을 선택하게 합니다.

## Base URL

배포 기준 URL:

```text
Frontend: https://pickbit.co.kr
Backend API: https://api.pickbit.co.kr
```

로컬 개발 기준 Gateway URL:

```text
http://localhost:18080
```

프론트 OAuth 페이지:

```text
/oauth/callback
/oauth/signup
/oauth/link
```

프론트 배포 환경 변수 예시:

```text
API_BASE_URL=https://api.pickbit.co.kr
OAUTH_KAKAO_LOGIN_URL=https://api.pickbit.co.kr/api/auth/oauth2/authorization/kakao
```

## OAuth 시작

프론트엔드는 OAuth provider와 직접 통신하지 않습니다. 사용자가 OAuth 버튼을 누르면 아래 백엔드 URL로 페이지 이동시키면 됩니다.

```http
GET /api/auth/oauth2/authorization/{provider}
```

지원 provider:

```text
google
kakao
naver
```

예시:

```text
배포: https://api.pickbit.co.kr/api/auth/oauth2/authorization/kakao
로컬: http://localhost:18080/api/auth/oauth2/authorization/kakao
```

## Provider Console 설정

카카오 Redirect URI에는 백엔드 OAuth 콜백 URL을 등록합니다.

```text
https://api.pickbit.co.kr/api/auth/oauth2/code/kakao
```

개발용을 유지하려면 아래도 함께 둡니다.

```text
http://localhost:18080/api/auth/oauth2/code/kakao
http://192.168.20.70:18080/api/auth/oauth2/code/kakao
```

카카오 사이트 도메인에는 프론트와 API 도메인을 등록합니다.

```text
https://pickbit.co.kr
https://api.pickbit.co.kr
```

현재 카카오 scope에는 `account_email`, `profile_nickname`을 요청하지 않습니다. 카카오 콘솔에서 권한이 없는 scope를 요청하면 `KOE205`가 발생합니다.

Google/Naver도 사용한다면 같은 패턴으로 배포용 Redirect URI를 등록합니다.

```text
https://api.pickbit.co.kr/api/auth/oauth2/code/google
https://api.pickbit.co.kr/api/auth/oauth2/code/naver
```

## 서버 Redirect 분기

OAuth provider 인증이 끝나면 백엔드가 provider 연결 상태에 따라 프론트로 redirect합니다.

### 1. 기존 OAuth 연결 계정

이미 해당 provider 계정이 픽빗 계정에 연결되어 있으면 callback 페이지로 이동합니다.

```text
https://pickbit.co.kr/oauth/callback?code={exchangeCode}
```

프론트는 `POST /api/auth/oauth/exchange`를 호출해 서비스 토큰으로 교환합니다.

### 2. 신규 OAuth 계정이며 이메일을 받은 경우

provider가 이메일을 제공했고 같은 이메일의 기존 픽빗 계정이 없으면 OAuth 가입 페이지로 이동합니다.

```text
https://pickbit.co.kr/oauth/signup?code={signupCode}
```

현재 카카오 설정에서는 이메일을 받을 수 없으므로 이 케이스는 카카오에서는 거의 발생하지 않습니다.

### 3. 신규 OAuth 계정이며 이메일이 없는 경우

provider 연결이 없고 이메일도 받을 수 없으면 연결/가입 선택 페이지로 이동합니다.

```text
https://pickbit.co.kr/oauth/link?linkCode={linkCode}&signupCode={signupCode}
```

프론트는 `/oauth/link` 화면에서 사용자가 아래 중 하나를 선택하게 합니다.

- 기존 계정 연결: `linkCode`로 `POST /api/auth/oauth/link` 호출
- 새 계정 생성: `signupCode`로 기존 OAuth signup API 호출

### 4. 신규 OAuth 계정이며 같은 이메일 계정이 있는 경우

나중에 카카오 `account_email` 권한을 얻으면 이 케이스가 동작합니다.

```text
https://pickbit.co.kr/oauth/link?code={linkCode}
```

이 경우 프론트는 기존 픽빗 계정 비밀번호만 입력받으면 됩니다.

## 기존 OAuth 로그인

서버가 아래 페이지로 redirect합니다.

```text
https://pickbit.co.kr/oauth/callback?code={exchangeCode}
```

프론트 처리:

1. URL query에서 `code`를 읽습니다.
2. 토큰 교환 API를 호출합니다.
3. 받은 토큰을 저장합니다.
4. 로그인 완료 페이지 또는 메인 페이지로 이동합니다.

### Token Exchange API

```http
POST /api/auth/oauth/exchange
Content-Type: application/json
```

Request:

```json
{
  "code": "exchange-code"
}
```

Response:

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

## OAuth 신규 가입

서버가 아래 페이지로 redirect합니다.

```text
https://pickbit.co.kr/oauth/signup?code={signupCode}
```

또는 `/oauth/link?linkCode=...&signupCode=...`에서 사용자가 “새 계정 생성”을 선택하면 `signupCode`로 같은 API를 사용합니다.

프론트 처리:

1. URL query에서 `code` 또는 `signupCode`를 읽습니다.
2. 가입 기본 정보 조회 API를 호출합니다.
3. 응답값을 email/nickname 입력 form 기본값으로 사용합니다.
4. 사용자가 email/nickname을 입력 또는 수정합니다.
5. OAuth 가입 완료 API를 호출합니다.
6. 받은 토큰을 저장합니다.
7. 로그인 완료 페이지 또는 메인 페이지로 이동합니다.

### Signup Context API

```http
GET /api/auth/oauth/signup-context?code={signupCode}
```

Response:

```json
{
  "provider": "KAKAO",
  "email": null,
  "nickname": null
}
```

`email` 또는 `nickname`은 `null`일 수 있습니다.

예시:

```json
{
  "provider": "KAKAO",
  "email": null,
  "nickname": null
}
```

Kakao는 이메일/닉네임 동의항목을 요청하지 않습니다.
Kakao OAuth는 계정 식별만 처리하고, 서비스 가입에 필요한 `email`과 `nickname`은 추가 정보 입력 페이지에서 직접 입력받습니다.

### OAuth Signup API

```http
POST /api/auth/oauth/signup
Content-Type: application/json
```

Request:

```json
{
  "code": "signup-code",
  "email": "user@example.com",
  "nickname": "tester"
}
```

Response:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 1800000,
  "refreshTokenExpiresIn": 1209600000
}
```

## 기존 계정 연결

카카오 이메일을 받을 수 없는 현재 상태에서는 `/oauth/link?linkCode=...&signupCode=...` 화면에서 사용자가 직접 기존 픽빗 이메일과 비밀번호를 입력합니다.

### Link Context API

```http
GET /api/auth/oauth/link-context?code={linkCode}
```

Response:

```json
{
  "provider": "KAKAO",
  "email": null,
  "nickname": null
}
```

`email`과 `nickname`은 `null`일 수 있습니다. `email`이 `null`이면 사용자가 직접 픽빗 이메일을 입력하게 합니다.

### OAuth Link API

```http
POST /api/auth/oauth/link
Content-Type: application/json
```

Request:

```json
{
  "code": "link-code",
  "email": "user@example.com",
  "password": "password123"
}
```

Response:

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 1800000,
  "refreshTokenExpiresIn": 1209600000
}
```

성공 시 백엔드는 토큰 쿠키도 함께 설정합니다. 이후 같은 카카오 계정으로 로그인하면 기존 픽빗 계정의 `accountId`로 토큰이 발급됩니다.

## 프론트 Validation

서버 validation 기준입니다. 프론트에서도 동일하게 검증하는 것을 권장합니다.

```text
email: 필수, 이메일 형식
nickname: 필수, 2자 이상 20자 이하
password: 필수, 8자 이상 72자 이하
```

## Error Handling

### 409 Conflict

아래 경우 발생합니다.

```text
이메일 또는 닉네임이 이미 DB에 존재함
이미 해당 OAuth provider가 계정에 연결됨
이미 해당 providerId가 다른 계정에 연결됨
```

프론트 처리 추천:

```text
가입 화면: form field error로 표시
연결 화면: 이미 연결된 계정입니다. 다시 로그인해주세요.
```

### 401 Unauthorized

아래 경우 발생합니다.

```text
code가 만료됨
code가 이미 사용됨
code가 올바르지 않음
기존 계정 비밀번호가 틀림
입력한 이메일의 픽빗 계정이 없음
```

프론트 처리 추천:

```text
OAuth 인증 시간이 만료되었습니다. 다시 로그인해주세요.
비밀번호가 올바르지 않습니다.
```

## Code 종류

OAuth flow에는 세 종류의 code가 있습니다.

```text
exchangeCode: 기존 OAuth 연결 계정 로그인 완료 후 토큰 교환용
signupCode: 신규 OAuth 가입 완료용
linkCode: 기존 픽빗 계정에 OAuth provider 연결용
```

각 code는 서로 다른 API에만 사용해야 합니다.

```text
exchangeCode -> POST /api/auth/oauth/exchange
signupCode   -> GET /api/auth/oauth/signup-context
signupCode   -> POST /api/auth/oauth/signup
linkCode     -> GET /api/auth/oauth/link-context
linkCode     -> POST /api/auth/oauth/link
```

## 전체 흐름 요약

```text
1. OAuth 버튼 클릭
2. GET /api/auth/oauth2/authorization/{provider} 로 이동
3. provider 로그인
4. 서버가 provider + providerId 연결 여부 판단
5-1. 기존 연결 계정: /oauth/callback?code={exchangeCode}
5-2. 이메일 있는 신규 계정: /oauth/signup?code={signupCode}
5-3. 이메일 없는 신규 계정: /oauth/link?linkCode={linkCode}&signupCode={signupCode}
6-1. 기존 연결 계정: POST /api/auth/oauth/exchange
6-2. 신규 가입 선택: GET /api/auth/oauth/signup-context -> POST /api/auth/oauth/signup
6-3. 기존 계정 연결 선택: GET /api/auth/oauth/link-context -> POST /api/auth/oauth/link
7. 토큰 저장 후 로그인 완료
```
