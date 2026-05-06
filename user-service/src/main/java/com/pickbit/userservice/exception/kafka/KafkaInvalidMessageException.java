package com.pickbit.userservice.exception.kafka;

public class KafkaInvalidMessageException extends RuntimeException {

    public KafkaInvalidMessageException(String targetClass, String cause) {
        super("메시지 역직렬화 실패. targetClass=%s, cause=%s".formatted(targetClass, cause));
    }

    public KafkaInvalidMessageException(String message) {
        super(message);
    }
}
