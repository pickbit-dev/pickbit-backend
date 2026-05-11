package com.pickbit.authservice.security.oauth;

import com.pickbit.authservice.domain.enums.OAuthProvider;

public record OAuthUserInfo(
        OAuthProvider provider,
        String providerId,
        String email,
        String nickname
) {
}
