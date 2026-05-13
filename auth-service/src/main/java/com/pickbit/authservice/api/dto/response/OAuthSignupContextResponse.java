package com.pickbit.authservice.api.dto.response;

import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.security.oauth.OAuthSignupContext;

public record OAuthSignupContextResponse(
        OAuthProvider provider,
        String email,
        String nickname
) {

    public static OAuthSignupContextResponse from(OAuthSignupContext context) {
        return new OAuthSignupContextResponse(context.provider(), context.email(), context.nickname());
    }
}
