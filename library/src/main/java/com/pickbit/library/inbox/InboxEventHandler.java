package com.pickbit.library.inbox;

import java.util.Set;

/**
 * Kafka 이벤트를 처리하는 핸들러입니다.
 *
 * <p>{@code @KafkaListener} 가 직접 호출하기도 하고, 처리에 실패해 인박스에 남은 이벤트를
 * 재처리 스케줄러가 다시 호출하기도 합니다. 두 경로가 같은 메서드를 타야 재처리가
 * 원래 처리와 정확히 같은 일을 합니다.
 *
 * <p>구현체는 반드시 멱등해야 합니다. 재전달과 재처리 모두 같은 이벤트를 두 번 넘길 수 있습니다.
 */
public interface InboxEventHandler {

    /** 이 핸들러가 담당하는 토픽. */
    String topic();

    /** 이 핸들러가 담당하는 action 헤더 값들. 한 리스너가 여러 action 을 처리하는 경우가 있다. */
    Set<String> actions();

    /**
     * 이벤트를 처리합니다.
     *
     * @param action       action 헤더 값
     * @param eventId      이벤트 고유 ID (멱등성 키)
     * @param aggregateId  Kafka 메시지 키
     * @param messageBody  원본 페이로드
     * @param eventVersion 아웃박스 행 ID. 순서 판별에 쓰이며, 헤더가 없으면 {@code null}
     */
    void handle(String action, String eventId, String aggregateId, String messageBody, Long eventVersion);
}
