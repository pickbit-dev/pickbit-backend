package com.pickbit.authservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그아웃 요청입니다.
 *
 * @param refreshToken 폐기할 refresh token
 */
public record LogoutRequest(
        @NotBlank(message = "refreshToken은 필수입니다.")
        String refreshToken
) {
}
