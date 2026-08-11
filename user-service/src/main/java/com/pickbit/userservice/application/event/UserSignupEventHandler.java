package com.pickbit.userservice.application.event;

import com.pickbit.userservice.application.InboxService;
import com.pickbit.userservice.domain.User;
import com.pickbit.userservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.userservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.userservice.exception.kafka.KafkaSyncException;
import com.pickbit.userservice.infrastructure.persistence.UserRepository;
import com.pickbit.library.inbox.InboxEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSignupEventHandler implements InboxEventHandler {

    public static final String TOPIC = "AuthAccount-topic";
    private static final String SIGNUP_ACTION = "SIGNUP";

    private final UserRepository userRepository;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleSignup(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, SIGNUP_ACTION);
        }

        UserSignupEvent event = eventHandlerSupport.deserialize(messageBody, UserSignupEvent.class);
        validateAggregateId(aggregateId, event);

        try {
            if (userRepository.findByAccountId(event.accountId()).isEmpty()) {
                User user = User.create(
                        event.accountId(),
                        event.email(),
                        createAvailableNickname(event.nickname(), event.accountId()),
                        event.provider(),
                        event.role()
                );
                userRepository.save(user);
            }
            inboxService.recordSuccess(eventId, TOPIC, SIGNUP_ACTION, aggregateId, messageBody, eventVersion);
            log.info("회원가입 이벤트 처리 완료. eventId={}, accountId={}", eventId, event.accountId());
        } catch (Exception e) {
            log.error("회원가입 이벤트 처리 실패. eventId={}, accountId={}", eventId, event.accountId(), e);
            inboxService.recordFailure(eventId, TOPIC, SIGNUP_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, SIGNUP_ACTION, e);
        }
    }

    private void validateAggregateId(String aggregateId, UserSignupEvent event) {
        if (event.accountId() == null || event.email() == null || event.provider() == null || event.role() == null) {
            throw new KafkaInvalidMessageException("accountId, email, provider, role은 필수입니다.");
        }
        String expectedAggregateId = "AuthAccount:" + event.accountId();
        if (!expectedAggregateId.equals(aggregateId)) {
            throw new KafkaInvalidMessageException(
                    "Kafka key와 payload accountId가 일치하지 않습니다. key=%s, expected=%s"
                            .formatted(aggregateId, expectedAggregateId));
        }
    }

    private String createAvailableNickname(String nickname, Long accountId) {
        String base = normalizeNickname(nickname, accountId);
        if (!userRepository.existsByNickname(base)) {
            return base;
        }

        for (int i = 0; i < 3; i++) {
            String candidate = truncate(base, 13) + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
        return truncate("user_" + accountId, 13) + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private String normalizeNickname(String nickname, Long accountId) {
        String normalized = StringUtils.hasText(nickname) ? nickname.replaceAll("\\s+", "") : "user_" + accountId;
        if (normalized.length() < 2) {
            normalized = "user_" + accountId;
        }
        return truncate(normalized, 20);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public Set<String> actions() {
        return Set.of(SIGNUP_ACTION);
    }

    /**
     * action 에 맞는 처리로 넘깁니다. Kafka 리스너와 인박스 재처리 스케줄러가 같은 진입점을 씁니다.
     */
    @Override
    public void handle(String action, String eventId, String aggregateId, String messageBody, Long eventVersion) {
        switch (action) {
            case SIGNUP_ACTION -> handleSignup(eventId, aggregateId, messageBody, eventVersion);
            default -> throw new IllegalArgumentException("지원하지 않는 action: " + action);
        }
    }
}
