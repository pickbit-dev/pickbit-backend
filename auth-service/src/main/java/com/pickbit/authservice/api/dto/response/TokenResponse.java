package com.pickbit.authservice.api.dto.response;

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
