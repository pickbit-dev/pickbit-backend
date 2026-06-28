# 카카오 KOE205 OAuth Scope 오류

## 증상

카카오 로그인 시 아래 오류가 발생했습니다.

```text
잘못된 요청 (KOE205)
설정하지 않은 카카오 로그인 동의 항목을 포함해 인가 코드를 요청했습니다.
설정하지 않은 동의 항목: account_email,profile_nickname
```

## 원인

백엔드 OAuth client 설정에서 카카오 scope로 `account_email`, `profile_nickname`을 요청했지만, 카카오 개발자 콘솔에서 해당 동의항목이 활성화되어 있지 않았습니다.

현재 카카오 앱 상태:

```text
profile_nickname: 사용 안 함
account_email: 권한 없음
```

카카오에서 권한이 없는 scope를 요청하면 인가 코드 발급 단계에서 `KOE205`로 차단됩니다.

## 해결

카카오 OAuth registration 설정에서 아래 scope 요청을 제거했습니다.

```yaml
scope:
  - account_email
  - profile_nickname
```

수정 대상:

```text
auth-service/src/main/resources/application-develop.yml
auth-service/src/main/resources/application-deploy.yml
auth-service/src/test/resources/application.yml
```

## 현재 정책

카카오 이메일을 받을 수 없으므로, 연결되지 않은 카카오 계정은 프론트의 연결/가입 선택 화면으로 보냅니다.

```text
https://pickbit.co.kr/oauth/link?linkCode={linkCode}&signupCode={signupCode}
```

프론트는 사용자가 아래 중 하나를 선택하게 합니다.

- 기존 픽빗 계정 연결: 이메일/비밀번호 입력 후 `POST /api/auth/oauth/link`
- 새 계정 생성: `signupCode`로 `POST /api/auth/oauth/signup`

## 재발 방지

- 카카오 콘솔에서 권한이 없는 scope는 설정 파일에 추가하지 않습니다.
- `account_email` 권한을 확보하기 전까지는 이메일 기반 자동 계정 매칭을 사용하지 않습니다.
- 나중에 카카오 비즈앱 전환/권한 승인을 완료하면 `account_email` scope를 다시 추가할 수 있습니다.
