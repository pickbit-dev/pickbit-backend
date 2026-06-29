# Gateway 인증 헤더 전파 문제

## 증상

도메인 서비스에서 인증 사용자 정보를 읽지 못하면 아래 문제가 발생합니다.

```text
AuthContextHolder.getUserId() 호출 실패
상품 등록/입찰/결제 API에서 사용자 식별 불가
닉네임이 필요한 API에서 nickname이 null로 처리됨
인증 필수 API가 Gateway를 통하지 않으면 인증 컨텍스트가 비어 있음
```

## 원인

Pickbit은 Gateway가 JWT를 검증하고 downstream 서비스로 인증 정보를 헤더로 전파하는 구조입니다.

도메인 서비스는 직접 JWT를 다시 파싱하지 않고, 공통 라이브러리의 `AuthContextFilter`가 요청 헤더를 읽어 `AuthContextHolder`에 저장합니다.

따라서 아래 상황에서는 인증 컨텍스트가 비어 있을 수 있습니다.

```text
Gateway 인증 필터가 실행되지 않음
Gateway가 인증 헤더를 downstream 요청에 주입하지 않음
서비스를 Gateway 우회로 직접 호출함
필터 순서가 꼬여 rate limit이나 라우팅보다 인증 처리가 늦게 실행됨
```

## 현재 처리

### Gateway

`AuthenticationGlobalFilter`가 JWT를 검증하고 사용자 정보를 헤더로 전파합니다.

주요 헤더:

```text
X-User-Id
X-User-Role
X-User-Nickname
X-User-Nickname-Encoded
X-User-Provider
X-User-Email
```

Gateway rate limit은 `X-User-Id`를 기준으로 사용자별 key를 만듭니다.

```text
RateLimiterConfig.userKeyResolver
-> X-User-Id 우선
-> 없으면 IP fallback
```

### Domain Services

공통 `library`의 `AuthContextFilter`가 인증 헤더를 읽어 `AuthContextHolder`에 저장합니다.

컨트롤러는 아래처럼 인증 정보를 사용합니다.

```java
AuthContextHolder.getUserId()
AuthContextHolder.getNickname()
```

사용 예:

```text
product-service: 상품 등록/수정/삭제
auction-service: 경매 생성/입찰/취소
payment-service: 결제 조회/confirm/취소/구매확정
notification-service: 알림 조회/읽음 처리
user-service: 내 정보 조회/수정
```

## 운영 주의

- 인증이 필요한 API는 반드시 Gateway를 통해 호출해야 합니다.
- 서비스 직접 호출이 필요한 내부 API는 별도 보안 정책을 둬야 합니다.
- nickname은 한글 등 비 ASCII 문자가 있을 수 있으므로 encoded header를 함께 사용합니다.
- Gateway route와 인증 제외 경로를 변경할 때 인증 필터 적용 범위를 같이 확인합니다.

## 재발 방지

- 신규 서비스는 `library`의 `AuthContextFilter` 자동 설정을 사용합니다.
- 신규 인증 필수 API는 `AuthContextHolder` 사용 여부를 테스트합니다.
- Gateway 필터 order를 변경할 때 rate limit의 `X-User-Id` 의존성을 확인합니다.
- 로컬 테스트에서 도메인 서비스를 직접 호출할 경우 인증 헤더를 수동으로 넣거나 Gateway 기준으로 테스트합니다.
