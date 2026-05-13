package com.pickbit.authservice.application.event;

import java.time.LocalDateTime;

public record UserNicknameUpdatedEvent(
        String eventId,
        Long accountId,
        String nickname,
        LocalDateTime updatedAt
) {
}
