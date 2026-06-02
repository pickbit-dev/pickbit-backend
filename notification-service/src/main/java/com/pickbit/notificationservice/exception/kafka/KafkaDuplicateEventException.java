package com.pickbit.notificationservice.exception.kafka;

public class KafkaDuplicateEventException extends RuntimeException {

    public KafkaDuplicateEventException(String eventId, String topic, String action) {
        super("이미 처리된 이벤트입니다. eventId=%s, topic=%s, action=%s".formatted(eventId, topic, action));
    }
}
