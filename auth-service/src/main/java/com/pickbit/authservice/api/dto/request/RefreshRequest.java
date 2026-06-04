package com.pickbit.authservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 액세스 토큰 재발급 요청입니다.
 *
 * @param refreshToken 재발급에 사용할 refresh token
 */
public record RefreshRequest(
        @NotBlank(message = "refreshToken은 필수입니다.")
        String refreshToken
) {
}
