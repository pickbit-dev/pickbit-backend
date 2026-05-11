# Auth Service 분리 기획

## 1. 목적

`auth-service`는 로그인, 토큰 발급, 토큰 재발급, 로그아웃 같은 인증 흐름을 담당합니다.

`user-service`는 사용자 프로필, 신뢰도, 패널티, 거래 제한 같은 사용자 도메인을 담당합니다.

두 서비스를 분리하면 인증 정책과 사용자 도메인 정책이 섞이지 않습니다.

```text
auth-service: 사용자가 누구인지 증명한다.
user-service: 그 사용자가 어떤 상태인지 관리한다.
```

---

## 2. 서비스 책임 분리

### auth-service

```text
회원가입 인증 흐름
로그인
비밀번호 검증
JWT access token 발급
refresh token 발급 및 재발급
로그아웃
OAuth 로그인 확장
인증 실패/토큰 만료 처리
```

### user-service

```text
사용자 프로필
닉네임/이메일/전화번호 관리
판매자/구매자 정보
패널티
신뢰도
경매 참여 제한
거래 제한 상태
```

---

## 3. 기본 로그인 흐름

```text
1. 사용자가 로그인 요청
2. auth-service가 계정 식별자와 비밀번호 검증
3. user-service에서 사용자 기본 상태 조회
4. 정지/탈퇴/제한 상태가 아니면 토큰 발급
5. 프론트는 access token으로 API 호출
6. access token 만료 시 refresh token으로 재발급
```

---

## 4. 초기 API 후보

### 회원가입

```http
POST /auth/signup
```

역할:

```text
로그인 계정 생성
비밀번호 암호화
user-service에 사용자 프로필 생성 요청
```

---

### 로그인

```http
POST /auth/login
```

역할:

```text
아이디/이메일 + 비밀번호 검증
사용자 상태 확인
access token 발급
refresh token 발급
```

---

### 토큰 재발급

```http
POST /auth/reissue
```

역할:

```text
refresh token 검증
새 access token 발급
필요 시 refresh token rotation
```

---

### 로그아웃

```http
POST /auth/logout
```

역할:

```text
refresh token 폐기
필요 시 access token blacklist 처리
```

---

### 인증 사용자 확인

```http
GET /auth/me
```

역할:

```text
현재 access token이 어떤 사용자를 가리키는지 확인
상세 프로필 조회는 user-service의 /users/me가 담당
```

---

## 5. 토큰 정책 초안

```text
access token: 짧은 만료 시간
refresh token: 긴 만료 시간
refresh token은 서버에 저장
로그아웃 시 refresh token 삭제
재발급 시 refresh token rotation 검토
```

프론트 처리:

```text
API 요청 시 access token 사용
401 응답이 오면 /auth/reissue 호출
재발급 성공 시 원래 요청 재시도
재발급 실패 시 로그인 화면으로 이동
```

---

## 6. user-service 연동

`auth-service`는 로그인 시 사용자 계정이 유효한지만 확인하지 않고, 사용자 상태도 확인해야 합니다.

예상 내부 API:

```http
GET /internal/users/{userId}/auth-state
```

응답 예시:

```json
{
  "userId": 1,
  "nickname": "buyer1",
  "status": "ACTIVE",
  "auctionRestricted": false,
  "tradeRestricted": false
}
```

로그인 차단 대상:

```text
탈퇴 사용자
정지 사용자
보안 잠금 사용자
```

로그인은 가능하지만 기능 제한이 필요한 대상:

```text
경매 참여 제한 사용자
거래 제한 사용자
결제 패널티 누적 사용자
```

---

## 7. OAuth 확장

초기에는 일반 로그인부터 구현하고, 이후 OAuth provider를 추가합니다.

후보:

```text
KAKAO
NAVER
GOOGLE
```

OAuth도 최종적으로는 같은 토큰 발급 흐름을 사용합니다.

```text
provider 인증 성공
-> auth-service 계정 매핑
-> user-service 사용자 상태 조회
-> access/refresh token 발급
```

---

## 8. 설계상 주의점

```text
비밀번호는 user-service가 아니라 auth-service가 관리한다.
사용자 프로필은 auth-service가 아니라 user-service가 관리한다.
JWT에는 최소한의 식별 정보만 넣는다.
권한/제한 상태는 오래 캐싱하지 않는다.
refresh token은 탈취 대응을 위해 서버 저장소에서 관리한다.
```

---

## 9. 구현 우선순위

### 1차

```text
auth-service 모듈 생성
AuthServiceApplication 추가
기본 profile 설정 추가
```

### 2차

```text
Account 엔티티
PasswordEncoder 설정
회원가입 API
로그인 API
JWT 발급 유틸
```

### 3차

```text
RefreshToken 저장소
토큰 재발급
로그아웃
Spring Security filter
```

### 4차

```text
user-service 내부 상태 조회 연동
계정 정지/탈퇴/제한 처리
```

### 5차

```text
OAuth 로그인
로그인 이력
비밀번호 재설정
2FA 또는 추가 인증
```
