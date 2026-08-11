package com.pickbit.gatewayservice.security;

/**
 * 게이트웨이가 확인한 호출자 신원입니다. 다운스트림 서비스에는 이 값이 {@code X-User-*} 헤더로 전달됩니다.
 *
 * @param accountId 인증 계정 ID
 * @param email     계정 이메일
 * @param nickname  사용자 닉네임
 * @param role      계정 역할 (USER / ADMIN)
 * @param provider  로그인 provider (LOCAL / GOOGLE / KAKAO / NAVER)
 */
public record AuthenticatedUser(
        Long accountId,
        String email,
        String nickname,
        String role,
        String provider
) {
}
