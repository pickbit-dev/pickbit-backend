package com.pickbit.authservice.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * OAuth 추가 회원가입 완료 요청입니다.
 *
 * @param code OAuth 가입 컨텍스트 확인 코드
 * @param email 가입 이메일
 * @param nickname 사용자 닉네임
 */
public record OAuthSignupCompleteRequest(
        @NotBlank(message = "code는 필수입니다.")
        String code,

        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해주세요.")
        String nickname
) {
}
