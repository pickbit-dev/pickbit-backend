package com.pickbit.authservice.api.dto.response;

import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.security.oauth.OAuthSignupContext;

/**
 * OAuth 추가 회원가입 컨텍스트 응답입니다.
 *
 * @param provider OAuth provider
 * @param email OAuth provider에서 받은 이메일
 * @param nickname OAuth provider에서 받은 닉네임
 */
public record OAuthSignupContextResponse(
        OAuthProvider provider,
        String email,
        String nickname
) {

    public static OAuthSignupContextResponse from(OAuthSignupContext context) {
        return new OAuthSignupContextResponse(context.provider(), context.email(), context.nickname());
    }
}
