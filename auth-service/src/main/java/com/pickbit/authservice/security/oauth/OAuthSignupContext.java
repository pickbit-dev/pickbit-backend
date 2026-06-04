package com.pickbit.authservice.security.oauth;

import com.pickbit.authservice.domain.enums.OAuthProvider;

/**
 * OAuth 추가 회원가입에 필요한 임시 컨텍스트입니다.
 *
 * @param provider OAuth provider
 * @param providerId OAuth provider 사용자 ID
 * @param email OAuth provider에서 받은 이메일
 * @param nickname OAuth provider에서 받은 닉네임
 */
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
