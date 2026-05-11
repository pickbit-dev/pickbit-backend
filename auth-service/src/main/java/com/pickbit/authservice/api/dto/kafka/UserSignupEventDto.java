package com.pickbit.authservice.api.dto.kafka;

import java.time.LocalDateTime;

public record UserSignupEventDto(
        String eventId,
        Long accountId,
        String email,
        String nickname,
        String provider,
        String role,
        LocalDateTime createdAt
) {
}
