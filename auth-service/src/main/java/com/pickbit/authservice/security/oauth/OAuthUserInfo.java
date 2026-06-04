package com.pickbit.authservice.security.oauth;

import com.pickbit.authservice.domain.enums.OAuthProvider;

/**
 * OAuth provider 사용자 정보입니다.
 *
 * @param provider OAuth provider
 * @param providerId OAuth provider 사용자 ID
 * @param email OAuth provider에서 받은 이메일
 * @param nickname OAuth provider에서 받은 닉네임
 */
public record OAuthUserInfo(
        OAuthProvider provider,
        String providerId,
        String email,
        String nickname
) {
}
