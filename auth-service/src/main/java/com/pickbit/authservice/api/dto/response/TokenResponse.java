package com.pickbit.authservice.api.dto.response;

/**
 * 인증 토큰 발급 응답입니다.
 *
 * @param accessToken access token
 * @param refreshToken refresh token
 * @param tokenType 토큰 타입
 * @param accessTokenExpiresIn access token 만료까지 남은 초
 * @param refreshTokenExpiresIn refresh token 만료까지 남은 초
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn,
        long refreshTokenExpiresIn
) {

    public static TokenResponse bearer(String accessToken, String refreshToken, long accessTokenExpiresIn, long refreshTokenExpiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", accessTokenExpiresIn, refreshTokenExpiresIn);
    }
}
