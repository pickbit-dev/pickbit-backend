package com.pickbit.userservice.api.dto;

import com.pickbit.userservice.domain.User;

/**
 * 사용자 조회 응답입니다.
 *
 * @param id 사용자 ID
 * @param accountId 인증 계정 ID
 * @param nickname 사용자 닉네임
 * @param email 사용자 이메일
 * @param profileImageUrl 프로필 이미지 URL
 * @param provider OAuth provider
 * @param role 사용자 역할
 */
public record UserResponse(
        Long id,
        Long accountId,
        String nickname,
        String email,
        String profileImageUrl,
        String provider,
        String role
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getAccountId(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getProvider(),
                user.getRole()
        );
    }
}
