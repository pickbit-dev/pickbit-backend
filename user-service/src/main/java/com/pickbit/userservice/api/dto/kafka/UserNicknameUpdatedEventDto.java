package com.pickbit.userservice.api.dto.kafka;

import java.time.LocalDateTime;

public record UserNicknameUpdatedEventDto(
        String eventId,
        Long accountId,
        String nickname,
        LocalDateTime updatedAt
) {
}
