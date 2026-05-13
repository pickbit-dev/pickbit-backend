package com.pickbit.library.auth;

public record AuthContext(
        Long userId,
        String role,
        String nickname,
        String provider,
        String email
) {
}
