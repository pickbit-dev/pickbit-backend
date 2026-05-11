package com.pickbit.authservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ValidateTokenRequest(
        @NotBlank(message = "token은 필수입니다.")
        String token
) {
}
