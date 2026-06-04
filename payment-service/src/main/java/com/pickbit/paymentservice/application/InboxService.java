package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.domain.Inbox;
import com.pickbit.paymentservice.infrastructure.persistence.InboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboxService {

    private final InboxRepository inboxRepository;

    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId) {
        return inboxRepository.existsBySuccessEventId(eventId);
    }

    @Transactional
    public void recordSuccess(String eventId, String topic, String action, String aggregateId, String messageBody) {
        Inbox inbox = Inbox.builder()
                .eventId(eventId)
                .successEventId(eventId)
                .topic(topic)
                .action(action)
                .aggregateId(aggregateId)
                .messageBody(messageBody)
                .processedAt(LocalDateTime.now())
                .success(true)
                .build();
        inboxRepository.save(inbox);
        log.info("Kafka 이벤트 처리 성공 기록. eventId={}, topic={}, action={}", eventId, topic, action);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String eventId, String topic, String action, String aggregateId, String messageBody, String errorMessage) {
        Inbox inbox = Inbox.builder()
                .eventId(eventId)
                .successEventId(null)
                .topic(topic)
                .action(action)
                .aggregateId(aggregateId)
                .messageBody(messageBody)
                .processedAt(LocalDateTime.now())
                .success(false)
                .errorMessage(errorMessage)
                .build();
        inboxRepository.save(inbox);
        log.info("Kafka 이벤트 처리 실패 기록. eventId={}, topic={}, action={}", eventId, topic, action);
    }
}
