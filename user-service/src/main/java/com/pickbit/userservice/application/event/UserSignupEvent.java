package com.pickbit.userservice.application.event;

import java.time.LocalDateTime;

public record UserSignupEvent(
        String eventId,
        Long accountId,
        String email,
        String nickname,
        String provider,
        String role,
        LocalDateTime createdAt
) {
}
