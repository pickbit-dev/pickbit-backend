# Frontend OAuth Flow

이 문서는 프론트엔드에서 OAuth 로그인/회원가입을 연동하기 위한 흐름을 설명합니다.

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

프론트엔드 OAuth 페이지:

```text
/oauth/callback
/oauth/signup
```

프론트 배포 환경 변수 예시:

```text
API_BASE_URL=https://api.pickbit.co.kr
OAUTH_KAKAO_LOGIN_URL=https://api.pickbit.co.kr/api/auth/oauth2/authorization/kakao
```

## OAuth 시작

프론트엔드는 OAuth provider와 직접 통신하지 않습니다.

사용자가 OAuth 버튼을 누르면 아래 백엔드 URL로 페이지 이동시키면 됩니다.

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

## OAuth Provider Console 설정

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

Google/Naver도 사용한다면 같은 패턴으로 배포용 Redirect URI를 등록합니다.

```text
https://api.pickbit.co.kr/api/auth/oauth2/code/google
https://api.pickbit.co.kr/api/auth/oauth2/code/naver
```

## 서버 Redirect 분기

OAuth provider 인증이 끝나면 서버가 사용자가 기존 회원인지 신규 회원인지 판단합니다.

기존 OAuth 계정이 있으면:

```text
https://pickbit.co.kr/oauth/callback?code={exchangeCode}
```

신규 OAuth 계정이면:

```text
https://pickbit.co.kr/oauth/signup?code={signupCode}
```

## 기존 회원 로그인

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

## 신규 회원 추가 정보 입력

서버가 아래 페이지로 redirect합니다.

```text
https://pickbit.co.kr/oauth/signup?code={signupCode}
```

프론트 처리:

1. URL query에서 `code`를 읽습니다.
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

## 프론트 Validation

서버 validation 기준입니다. 프론트에서도 동일하게 검증하는 것을 권장합니다.

```text
email: 필수, 이메일 형식
nickname: 필수, 2자 이상 20자 이하
```

## Error Handling

### 409 Conflict

이메일 또는 닉네임이 이미 DB에 존재할 때 발생합니다.

예상 메시지:

```text
이미 가입된 이메일입니다. email=user@example.com
이미 사용 중인 닉네임입니다. nickname=tester
```

프론트 처리 추천:

```text
form field error로 표시
```

### 401 Unauthorized

`code`가 만료됐거나 이미 사용됐거나 올바르지 않을 때 발생합니다.

프론트 처리 추천:

```text
OAuth 인증 시간이 만료되었습니다. 다시 로그인해주세요.
```

이후 OAuth 시작 화면으로 이동시키면 됩니다.

## Code 종류

OAuth flow에는 두 종류의 code가 있습니다.

```text
exchangeCode: 기존 회원 로그인 완료 후 토큰 교환용
signupCode: 신규 회원 추가 정보 입력 및 가입 완료용
```

각 code는 서로 다른 API에만 사용해야 합니다.

```text
exchangeCode -> POST /api/auth/oauth/exchange
signupCode   -> GET /api/auth/oauth/signup-context
signupCode   -> POST /api/auth/oauth/signup
```

## 전체 흐름 요약

```text
1. OAuth 버튼 클릭
2. GET /api/auth/oauth2/authorization/{provider} 로 이동
3. provider 로그인
4. 서버가 기존 회원 여부 판단
5-1. 기존 회원: /oauth/callback?code={exchangeCode}
5-2. 신규 회원: /oauth/signup?code={signupCode}
6-1. 기존 회원: POST /api/auth/oauth/exchange
6-2. 신규 회원: GET /api/auth/oauth/signup-context
7-2. 신규 회원: email/nickname 입력
8-2. 신규 회원: POST /api/auth/oauth/signup
9. 토큰 저장 후 로그인 완료
```
