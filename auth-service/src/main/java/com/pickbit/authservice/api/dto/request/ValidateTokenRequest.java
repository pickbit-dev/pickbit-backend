package com.pickbit.authservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 토큰 검증 요청입니다.
 *
 * @param token 검증할 access token
 */
public record ValidateTokenRequest(
        @NotBlank(message = "token은 필수입니다.")
        String token
) {
}
