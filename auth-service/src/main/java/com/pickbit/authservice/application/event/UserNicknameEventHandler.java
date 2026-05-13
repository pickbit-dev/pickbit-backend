package com.pickbit.authservice.application.event;

import com.pickbit.authservice.application.InboxService;
import com.pickbit.authservice.domain.AuthAccount;
import com.pickbit.authservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.authservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.authservice.exception.kafka.KafkaSyncException;
import com.pickbit.authservice.infrastructure.persistence.AuthAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserNicknameEventHandler {

    public static final String TOPIC = "User-topic";
    public static final String NICKNAME_UPDATED_ACTION = "NICKNAME_UPDATED";

    private final AuthAccountRepository authAccountRepository;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleNicknameUpdated(String eventId, String aggregateId, String messageBody) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, NICKNAME_UPDATED_ACTION);
        }

        UserNicknameUpdatedEvent event = eventHandlerSupport.deserialize(messageBody, UserNicknameUpdatedEvent.class);
        validateAggregateId(aggregateId, event);

        try {
            AuthAccount account = authAccountRepository.findById(event.accountId())
                    .orElseThrow(() -> new KafkaInvalidMessageException("존재하지 않는 계정입니다. accountId=" + event.accountId()));
            account.changeNickname(event.nickname());
            inboxService.recordSuccess(eventId, TOPIC, NICKNAME_UPDATED_ACTION, aggregateId, messageBody);
            log.info("닉네임 변경 이벤트 처리 완료. eventId={}, accountId={}", eventId, event.accountId());
        } catch (Exception e) {
            log.error("닉네임 변경 이벤트 처리 실패. eventId={}, accountId={}", eventId, event.accountId(), e);
            inboxService.recordFailure(eventId, TOPIC, NICKNAME_UPDATED_ACTION, aggregateId, messageBody, e.getMessage());
            throw new KafkaSyncException(eventId, NICKNAME_UPDATED_ACTION, e);
        }
    }

    private void validateAggregateId(String aggregateId, UserNicknameUpdatedEvent event) {
        if (event.accountId() == null || !StringUtils.hasText(event.nickname())) {
            throw new KafkaInvalidMessageException("accountId와 nickname은 필수입니다.");
        }
        String expectedAggregateId = "User:" + event.accountId();
        if (!expectedAggregateId.equals(aggregateId)) {
            throw new KafkaInvalidMessageException(
                    "Kafka key와 payload accountId가 일치하지 않습니다. key=%s, expected=%s"
                            .formatted(aggregateId, expectedAggregateId));
        }
    }
}
