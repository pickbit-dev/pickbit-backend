package com.pickbit.userservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자 프로필 수정 요청입니다.
 *
 * @param nickname 변경할 닉네임
 * @param profileImageUrl 변경할 프로필 이미지 URL
 */
public record ProfileUpdateRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해주세요.")
        String nickname,

        @Size(max = 500, message = "프로필 이미지 URL은 500자 이하로 입력해주세요.")
        String profileImageUrl
) {
}
