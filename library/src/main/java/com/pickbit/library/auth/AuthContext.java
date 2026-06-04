package com.pickbit.library.auth;

/**
 * 게이트웨이 인증 컨텍스트입니다.
 *
 * @param userId 인증 사용자 ID
 * @param role 사용자 역할
 * @param nickname 사용자 닉네임
 * @param provider OAuth provider
 * @param email 사용자 이메일
 */
public record AuthContext(
        Long userId,
        String role,
        String nickname,
        String provider,
        String email
) {
}
