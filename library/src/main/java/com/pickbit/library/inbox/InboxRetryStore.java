package com.pickbit.library.inbox;

import java.util.List;

/**
 * 재처리 스케줄러가 인박스에 접근하기 위한 계약입니다.
 *
 * <p>Inbox 엔티티는 서비스마다 각자의 스키마에 있으므로 라이브러리가 직접 다루지 않고,
 * 각 서비스의 InboxService 가 이 인터페이스를 구현합니다.
 */
public interface InboxRetryStore {

    /**
     * 재처리 대상을 가져옵니다.
     *
     * <p>성공 기록이 없고, 시도 횟수가 상한 미만이며, 다음 시도 시각이 지난 행만 대상입니다.
     */
    List<FailedInboxEvent> findRetryable(int maxAttempts, int limit);

    /** 재처리가 성공했음을 기록합니다. */
    void markRetrySucceeded(Long inboxId);

    /**
     * 재처리가 실패했음을 기록합니다. 시도 횟수를 올리고 다음 시도 시각을 뒤로 미룹니다.
     *
     * @param backoffSeconds 다음 시도까지 기다릴 시간
     */
    void markRetryFailed(Long inboxId, String errorMessage, long backoffSeconds);

    /**
     * 인라인 재시도가 모두 소진돼 오프셋이 넘어가기 직전에 호출됩니다.
     *
     * <p>핸들러가 이미 실패를 기록했다면 중복 기록하지 않아야 합니다. 핸들러에 도달하기 전에
     * 터진 예외(헤더 누락 등)를 놓치지 않기 위한 안전망입니다.
     */
    void recordExhausted(String eventId, String topic, String action, String aggregateId,
                         String messageBody, Long eventVersion, String errorMessage);
}
