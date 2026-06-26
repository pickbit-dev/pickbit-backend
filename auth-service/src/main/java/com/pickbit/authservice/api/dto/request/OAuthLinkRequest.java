package com.pickbit.authservice.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * OAuth provider account linking request.
 */
public record OAuthLinkRequest(
        @NotBlank(message = "code는 필수입니다.")
        String code,

        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하로 입력해주세요.")
        String password
) {
}
