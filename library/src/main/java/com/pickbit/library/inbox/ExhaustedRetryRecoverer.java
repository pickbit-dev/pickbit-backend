package com.pickbit.library.inbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

import java.nio.charset.StandardCharsets;

/**
 * 인라인 재시도가 모두 소진된 레코드를 처리합니다.
 *
 * <p>이걸 지정하지 않으면 {@code DefaultErrorHandler} 의 기본 recoverer 가 로그만 찍고
 * 오프셋을 넘깁니다. 즉 <b>이벤트가 조용히 사라집니다.</b> 실제로 결제 생성 이벤트가
 * 이 경로로 유실될 수 있었습니다.
 *
 * <p>여기서는 실패를 인박스에 확실히 남겨 재처리 스케줄러가 이어받게 합니다.
 * 오프셋은 그대로 넘깁니다 — 붙잡고 있으면 그 파티션의 뒤 이벤트가 전부 막히기 때문입니다.
 */
@Slf4j
@RequiredArgsConstructor
public class ExhaustedRetryRecoverer implements ConsumerRecordRecoverer {

    private static final String HEADER_EVENT_ID = "event_id";
    private static final String HEADER_ACTION = "action";
    private static final String HEADER_EVENT_VERSION = "event_version";

    private final InboxRetryStore store;

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        String eventId = header(record, HEADER_EVENT_ID);
        String action = header(record, HEADER_ACTION);
        String aggregateId = record.key() == null ? null : String.valueOf(record.key());
        String messageBody = record.value() == null ? null : String.valueOf(record.value());

        log.error("인라인 재시도 소진 | topic={} | partition={} | offset={} | eventId={} | action={}",
                record.topic(), record.partition(), record.offset(), eventId, action, exception);

        if (eventId == null || action == null) {
            // 재처리에 필요한 정보가 없다. 더 할 수 있는 일이 없으므로 로그로만 남긴다.
            log.error("이벤트 식별 헤더가 없어 재처리 대상으로 등록하지 못했습니다. body={}", messageBody);
            return;
        }

        try {
            store.recordExhausted(eventId, record.topic(), action, aggregateId, messageBody,
                    parseVersion(header(record, HEADER_EVENT_VERSION)), rootMessage(exception));
        } catch (RuntimeException e) {
            // 여기서 던지면 오프셋이 넘어가지 않아 파티션이 막힌다. 로그만 남기고 진행한다.
            log.error("실패 이벤트 기록에 실패했습니다. eventId={}", eventId, e);
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
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

    private static String rootMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
