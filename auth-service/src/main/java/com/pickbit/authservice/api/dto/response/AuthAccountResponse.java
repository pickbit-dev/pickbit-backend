package com.pickbit.authservice.api.dto.response;

import com.pickbit.authservice.domain.AuthAccount;
import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.domain.enums.Role;

public record AuthAccountResponse(
        Long accountId,
        String email,
        OAuthProvider provider,
        Role role
) {

    public static AuthAccountResponse from(AuthAccount account) {
        return new AuthAccountResponse(account.getId(), account.getEmail(), account.getOauthProvider(), account.getRole());
    }
}
