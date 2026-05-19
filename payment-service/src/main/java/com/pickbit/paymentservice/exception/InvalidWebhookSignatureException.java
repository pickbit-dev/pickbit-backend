package com.pickbit.paymentservice.exception;

public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException() {
        super("유효하지 않은 webhook 서명입니다.");
    }
}
