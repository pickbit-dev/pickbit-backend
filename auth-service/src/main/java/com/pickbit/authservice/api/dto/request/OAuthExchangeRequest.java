package com.pickbit.authservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * OAuth 인가 코드 교환 요청입니다.
 *
 * @param code OAuth provider에서 받은 인가 코드
 */
public record OAuthExchangeRequest(
        @NotBlank(message = "code는 필수입니다.")
        String code
) {
}
