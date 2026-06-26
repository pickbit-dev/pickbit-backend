package com.pickbit.authservice.security.oauth;

import com.pickbit.authservice.domain.enums.OAuthProvider;

/**
 * Existing account verification context for linking an OAuth provider.
 */
public record OAuthLinkContext(
        Long accountId,
        OAuthProvider provider,
        String providerId,
        String email,
        String nickname
) {

    public static OAuthLinkContext from(Long accountId, OAuthUserInfo userInfo) {
        return new OAuthLinkContext(
                accountId,
                userInfo.provider(),
                userInfo.providerId(),
                userInfo.email(),
                userInfo.nickname()
        );
    }

    public static OAuthLinkContext manual(OAuthUserInfo userInfo) {
        return from(null, userInfo);
    }
}
