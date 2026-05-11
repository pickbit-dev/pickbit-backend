package com.pickbit.userservice.api.dto;

import com.pickbit.userservice.domain.User;

public record UserResponse(
        Long id,
        Long accountId,
        String nickname,
        String email,
        String profileImageUrl,
        String provider,
        String role,
        Boolean nicknameVerified
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getAccountId(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getProvider(),
                user.getRole(),
                user.getNicknameVerified()
        );
    }
}
