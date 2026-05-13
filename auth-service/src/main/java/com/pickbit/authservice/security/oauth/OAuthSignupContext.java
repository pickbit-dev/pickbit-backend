package com.pickbit.authservice.security.oauth;

import com.pickbit.authservice.domain.enums.OAuthProvider;

public record OAuthSignupContext(
        OAuthProvider provider,
        String providerId,
        String email,
        String nickname
) {

    public static OAuthSignupContext from(OAuthUserInfo userInfo) {
        return new OAuthSignupContext(
                userInfo.provider(),
                userInfo.providerId(),
                userInfo.email(),
                userInfo.nickname()
        );
    }
}
