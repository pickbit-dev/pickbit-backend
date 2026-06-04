package com.pickbit.gatewayservice.dto;

import java.time.Instant;

/**
 * auth-service 토큰 검증 응답입니다.
 *
 * @param accountId 인증 계정 ID
 * @param email 계정 이메일
 * @param nickname 사용자 닉네임
 * @param role 계정 역할
 * @param provider OAuth provider
 * @param expiresAt 토큰 만료 시각
 */
public record AuthValidateResponse(
        Long accountId,
        String email,
        String nickname,
        String role,
        String provider,
        Instant expiresAt
) {
}
