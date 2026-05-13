package com.pickbit.authservice.security.oauth;

import com.pickbit.authservice.api.dto.response.TokenResponse;

public record OAuthLoginResult(
        TokenResponse tokenResponse,
        OAuthSignupContext signupContext
) {

    public static OAuthLoginResult authenticated(TokenResponse tokenResponse) {
        return new OAuthLoginResult(tokenResponse, null);
    }

    public static OAuthLoginResult signupRequired(OAuthSignupContext signupContext) {
        return new OAuthLoginResult(null, signupContext);
    }

    public boolean requiresSignup() {
        return signupContext != null;
    }
}
