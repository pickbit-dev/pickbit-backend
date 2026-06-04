package com.pickbit.userservice.application.event;

import java.time.LocalDateTime;

/**
 * 사용자 가입 이벤트입니다.
 *
 * @param eventId 이벤트 ID
 * @param accountId 인증 계정 ID
 * @param email 가입 이메일
 * @param nickname 사용자 닉네임
 * @param provider OAuth provider
 * @param role 사용자 역할
 * @param createdAt 가입 일시
 */
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
