package com.pickbit.notificationservice.exception.kafka;

public class KafkaSyncException extends RuntimeException {

    public KafkaSyncException(String eventId, String action, Throwable cause) {
        super("알림 이벤트 처리 실패. eventId=%s, action=%s".formatted(eventId, action), cause);
    }
}
