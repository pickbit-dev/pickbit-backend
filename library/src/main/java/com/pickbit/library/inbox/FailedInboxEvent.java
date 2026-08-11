package com.pickbit.library.inbox;

/**
 * 재처리 대상인 실패 이벤트입니다. 서비스별 Inbox 엔티티를 라이브러리로 끌어오지 않기 위한 뷰입니다.
 *
 * @param inboxId      인박스 행 ID
 * @param eventId      이벤트 고유 ID
 * @param topic        원본 토픽
 * @param action       action 헤더 값
 * @param aggregateId  메시지 키
 * @param messageBody  원본 페이로드
 * @param eventVersion 아웃박스 행 ID (없으면 null)
 * @param attemptCount 지금까지의 재처리 시도 횟수
 */
public record FailedInboxEvent(
        Long inboxId,
        String eventId,
        String topic,
        String action,
        String aggregateId,
        String messageBody,
        Long eventVersion,
        int attemptCount
) {
}
