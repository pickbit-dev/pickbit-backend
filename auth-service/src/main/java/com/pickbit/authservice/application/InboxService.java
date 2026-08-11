package com.pickbit.authservice.application;

import com.pickbit.authservice.domain.Inbox;
import com.pickbit.authservice.infrastructure.persistence.InboxRepository;
import com.pickbit.library.inbox.FailedInboxEvent;
import com.pickbit.library.inbox.InboxRetryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxService implements InboxRetryStore {

    private final InboxRepository inboxRepository;

    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId) {
        return inboxRepository.existsBySuccessEventId(eventId);
    }

    @Transactional
    public void recordSuccess(String eventId, String topic, String action, String aggregateId, String messageBody, Long eventVersion) {
        Inbox inbox = Inbox.builder()
                .eventId(eventId)
                .successEventId(eventId)
                .topic(topic)
                .action(action)
                .aggregateId(aggregateId)
                .messageBody(messageBody)
                .processedAt(LocalDateTime.now())
                .eventVersion(eventVersion)
                .success(true)
                .build();
        inboxRepository.save(inbox);
        log.info("Kafka 이벤트 처리 성공 기록. eventId={}, topic={}, action={}", eventId, topic, action);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String eventId, String topic, String action, String aggregateId, String messageBody, String errorMessage, Long eventVersion) {
        Inbox inbox = Inbox.builder()
                .eventId(eventId)
                .successEventId(null)
                .topic(topic)
                .action(action)
                .aggregateId(aggregateId)
                .messageBody(messageBody)
                .processedAt(LocalDateTime.now())
                .eventVersion(eventVersion)
                .success(false)
                .errorMessage(errorMessage)
                .build();
        inboxRepository.save(inbox);
        log.info("Kafka 이벤트 처리 실패 기록. eventId={}, topic={}, action={}", eventId, topic, action);
    }

    /**
     * 같은 aggregate 에 이미 더 최신 이벤트가 반영됐는지 확인합니다.
     *
     * <p>보통은 Kafka 가 파티션 단위로 순서를 보장하지만, 실패한 이벤트를 재처리 스케줄러가
     * 나중에 다시 처리하면 그 보장이 깨집니다. 뒤늦게 도착한 오래된 이벤트가 최신 상태를
     * 덮어쓰지 않도록 막습니다.
     */
    @Transactional(readOnly = true)
    public boolean isStale(String topic, String aggregateId, Long eventVersion) {
        if (eventVersion == null || aggregateId == null) {
            return false;
        }
        return inboxRepository
                .existsByTopicAndAggregateIdAndSuccessTrueAndEventVersionGreaterThanEqual(topic, aggregateId, eventVersion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FailedInboxEvent> findRetryable(int maxAttempts, int limit) {
        return inboxRepository.findRetryable(maxAttempts, LocalDateTime.now(), PageRequest.of(0, limit)).stream()
                .map(i -> new FailedInboxEvent(i.getId(), i.getEventId(), i.getTopic(), i.getAction(),
                        i.getAggregateId(), i.getMessageBody(), i.getEventVersion(), i.getAttemptCount()))
                .toList();
    }

    @Override
    @Transactional
    public void markRetrySucceeded(Long inboxId) {
        inboxRepository.findById(inboxId).ifPresent(Inbox::markRetrySucceeded);
    }

    @Override
    @Transactional
    public void markRetryFailed(Long inboxId, String errorMessage, long backoffSeconds) {
        inboxRepository.findById(inboxId)
                .ifPresent(i -> i.markRetryFailed(errorMessage, LocalDateTime.now().plusSeconds(backoffSeconds)));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExhausted(String eventId, String topic, String action, String aggregateId,
                                String messageBody, Long eventVersion, String errorMessage) {
        // 핸들러가 이미 실패를 남겼다면 중복 기록하지 않는다.
        if (inboxRepository.findByEventIdAndSuccessFalse(eventId).isPresent()) {
            return;
        }
        recordFailure(eventId, topic, action, aggregateId, messageBody, errorMessage, eventVersion);
    }
}
