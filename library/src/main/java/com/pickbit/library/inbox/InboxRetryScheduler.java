package com.pickbit.library.inbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 처리에 실패해 인박스에 남은 Kafka 이벤트를 다시 처리합니다.
 *
 * <p>인라인 재시도(약 1분)를 넘긴 실패는 오프셋이 이미 지나가 있어 Kafka 가 다시 주지 않습니다.
 * 이 스케줄러가 없으면 그 이벤트는 영영 사라집니다. 실제로 결제 생성 이벤트가 이 경로로
 * 유실될 수 있었습니다.
 *
 * <p>인박스에 원본 페이로드가 그대로 저장되어 있어 별도 저장소 없이 재처리가 가능합니다.
 * 파티션을 붙잡지 않으므로 긴 호흡으로 재시도해도 다른 이벤트 처리를 막지 않습니다.
 */
@Slf4j
@Component
public class InboxRetryScheduler {

    private final InboxRetryProperties properties;
    private final InboxRetryStore store;
    private final Map<String, InboxEventHandler> handlers;

    public InboxRetryScheduler(
            InboxRetryProperties properties,
            InboxRetryStore store,
            List<InboxEventHandler> handlers) {
        this.properties = properties;
        this.store = store;
        Map<String, InboxEventHandler> byTopicAndAction = new HashMap<>();
        for (InboxEventHandler handler : handlers) {
            for (String action : handler.actions()) {
                byTopicAndAction.put(key(handler.topic(), action), handler);
            }
        }
        this.handlers = Map.copyOf(byTopicAndAction);
    }

    @Scheduled(cron = "${inbox.retry.cron:0 */2 * * * *}")
    public void retryFailedEvents() {
        if (!properties.isEnabled() || handlers.isEmpty()) {
            return;
        }

        List<FailedInboxEvent> pending =
                store.findRetryable(properties.getMaxAttempts(), properties.getBatchSize());
        if (pending.isEmpty()) {
            return;
        }

        int succeeded = 0;
        int failed = 0;
        for (FailedInboxEvent event : pending) {
            if (retry(event)) {
                succeeded++;
            } else {
                failed++;
            }
        }
        log.info("인박스 재처리 완료 | 대상={} | 성공={} | 실패={}", pending.size(), succeeded, failed);
    }

    private boolean retry(FailedInboxEvent event) {
        InboxEventHandler handler = handlers.get(key(event.topic(), event.action()));
        if (handler == null) {
            // 이 서비스가 더 이상 담당하지 않는 이벤트다. 계속 조회 대상이 되지 않도록 종료 처리한다.
            log.warn("재처리할 핸들러가 없어 종료 처리합니다 | topic={} | action={} | eventId={}",
                    event.topic(), event.action(), event.eventId());
            store.markRetryFailed(event.inboxId(), "핸들러 없음", Long.MAX_VALUE / 2);
            return false;
        }

        try {
            handler.handle(event.action(), event.eventId(), event.aggregateId(),
                    event.messageBody(), event.eventVersion());
            store.markRetrySucceeded(event.inboxId());
            log.info("인박스 재처리 성공 | eventId={} | action={} | 시도={}",
                    event.eventId(), event.action(), event.attemptCount() + 1);
            return true;
        } catch (RuntimeException e) {
            long backoff = backoffSeconds(event.attemptCount() + 1);
            store.markRetryFailed(event.inboxId(), e.getMessage(), backoff);
            log.warn("인박스 재처리 실패 | eventId={} | action={} | 시도={} | 다음 시도까지={}초",
                    event.eventId(), event.action(), event.attemptCount() + 1, backoff, e);
            return false;
        }
    }

    /** 시도할수록 간격을 벌린다. 계속 실패하는 이벤트가 매 주기 DB를 두드리지 않게 한다. */
    private long backoffSeconds(int attempt) {
        long backoff = properties.getBaseBackoffSeconds() * (1L << Math.min(attempt - 1, 20));
        return Math.min(backoff, properties.getMaxBackoffSeconds());
    }

    private static String key(String topic, String action) {
        return topic + "|" + action;
    }
}
