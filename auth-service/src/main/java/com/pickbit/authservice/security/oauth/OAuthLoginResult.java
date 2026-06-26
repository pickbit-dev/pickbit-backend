package com.pickbit.authservice.security.oauth;

import com.pickbit.authservice.api.dto.response.TokenResponse;

/**
 * OAuth 로그인 처리 결과입니다.
 *
 * @param tokenResponse 인증이 완료된 경우 발급된 토큰 응답
 * @param signupContext 추가 회원가입이 필요한 경우 사용할 컨텍스트
 */
public record OAuthLoginResult(
        TokenResponse tokenResponse,
        OAuthSignupContext signupContext,
        OAuthLinkContext linkContext
) {

    public static OAuthLoginResult authenticated(TokenResponse tokenResponse) {
        return new OAuthLoginResult(tokenResponse, null, null);
    }

    public static OAuthLoginResult signupRequired(OAuthSignupContext signupContext) {
        return new OAuthLoginResult(null, signupContext, null);
    }

    public static OAuthLoginResult linkRequired(OAuthLinkContext linkContext) {
        return new OAuthLoginResult(null, null, linkContext);
    }

    public static OAuthLoginResult manualResolutionRequired(OAuthSignupContext signupContext, OAuthLinkContext linkContext) {
        return new OAuthLoginResult(null, signupContext, linkContext);
    }

    public boolean requiresSignup() {
        return signupContext != null;
    }

    public boolean requiresLink() {
        return linkContext != null;
    }
}
