package com.pickbit.productservice.exception.kafka;

public class KafkaSyncException extends RuntimeException {

    public KafkaSyncException(String eventId, String action, Throwable cause) {
        super("동기화 실패. eventId=%s, action=%s".formatted(eventId, action), cause);
    }
}
