package com.pickbit.userservice.infrastructure.kafka;

import com.pickbit.userservice.application.event.UserSignupEventHandler;
import com.pickbit.userservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.userservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.userservice.exception.kafka.KafkaSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSignupEventListener {

    private final UserSignupEventHandler eventHandler;

    @KafkaListener(
            topics = UserSignupEventHandler.TOPIC,
            containerFactory = "jsonKafkaListenerContainerFactory"
    )
    public void handleSignupMessage(
            @Header("action") String action,
            @Header("event_id") String eventId,
            // 아웃박스 행 ID. 커넥터 갱신 전에 발행된 메시지에는 없을 수 있어 필수가 아니다.
            @Header(name = "event_version", required = false) String eventVersion,
            @Header(KafkaHeaders.RECEIVED_KEY) String aggregateId,
            @Payload String messageBody
    ) {
        try {
            if ("SIGNUP".equals(action)) {
                eventHandler.handleSignup(eventId, aggregateId, messageBody, parseVersion(eventVersion));
            } else {
                log.warn("지원하지 않는 회원 action: {}", action);
            }
        } catch (KafkaDuplicateEventException e) {
            log.warn("중복 이벤트 스킵: {}", e.getMessage());
        } catch (KafkaInvalidMessageException e) {
            log.error("메시지 파싱 실패 - 재처리 불가: action={}, eventId={}, error={}", action, eventId, e.getMessage());
        } catch (KafkaSyncException e) {
            log.error("회원 동기화 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("예상치 못한 오류: action={}, eventId={}", action, eventId, e);
            throw e;
        }
    }

    private static Long parseVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
