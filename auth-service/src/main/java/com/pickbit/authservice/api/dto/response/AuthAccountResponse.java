package com.pickbit.authservice.api.dto.response;

import com.pickbit.authservice.domain.AuthAccount;
import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.domain.enums.Role;

/**
 * 인증 계정 조회 응답입니다.
 *
 * @param accountId 인증 계정 ID
 * @param email 계정 이메일
 * @param provider OAuth provider
 * @param role 계정 역할
 */
public record AuthAccountResponse(
        Long accountId,
        String email,
        OAuthProvider provider,
        Role role
) {

    public static AuthAccountResponse from(AuthAccount account) {
        return from(account, OAuthProvider.LOCAL);
    }

    public static AuthAccountResponse from(AuthAccount account, OAuthProvider provider) {
        return new AuthAccountResponse(account.getId(), account.getEmail(), provider, account.getRole());
    }
}
