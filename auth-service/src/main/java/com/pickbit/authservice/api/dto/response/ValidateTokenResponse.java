package com.pickbit.authservice.api.dto.response;

import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.domain.enums.Role;

import java.time.Instant;

public record ValidateTokenResponse(
        Long accountId,
        String email,
        Role role,
        OAuthProvider provider,
        Instant expiresAt
) {
}
