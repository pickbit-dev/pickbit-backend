package com.pickbit.authservice.api.dto.response;

import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.security.oauth.OAuthLinkContext;

/**
 * OAuth provider account linking screen context.
 */
public record OAuthLinkContextResponse(
        OAuthProvider provider,
        String email,
        String nickname
) {

    public static OAuthLinkContextResponse from(OAuthLinkContext context) {
        return new OAuthLinkContextResponse(context.provider(), context.email(), context.nickname());
    }
}
