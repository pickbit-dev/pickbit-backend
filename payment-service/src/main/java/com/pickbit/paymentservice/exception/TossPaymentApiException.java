package com.pickbit.paymentservice.exception;

import lombok.Getter;

@Getter
public class TossPaymentApiException extends RuntimeException {

    private final String errorCode;

    public TossPaymentApiException(String errorCode, String message) {
        super("토스 결제 API 오류: code=%s, message=%s".formatted(errorCode, message));
        this.errorCode = errorCode;
    }
}
