package com.pickbit.gatewayservice.dto;

import java.time.Instant;

public record AuthValidateResponse(
        Long accountId,
        String email,
        String role,
        String provider,
        Instant expiresAt
) {
}
