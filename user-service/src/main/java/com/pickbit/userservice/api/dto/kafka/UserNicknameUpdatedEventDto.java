package com.pickbit.userservice.api.dto.kafka;

import java.time.LocalDateTime;

/**
 * 사용자 닉네임 변경 Kafka 이벤트 payload입니다.
 *
 * @param eventId 이벤트 ID
 * @param accountId 인증 계정 ID
 * @param nickname 변경된 닉네임
 * @param updatedAt 변경 일시
 */
public record UserNicknameUpdatedEventDto(
        String eventId,
        Long accountId,
        String nickname,
        LocalDateTime updatedAt
) {
}
