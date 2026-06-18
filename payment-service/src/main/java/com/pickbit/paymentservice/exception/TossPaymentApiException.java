package com.pickbit.paymentservice.exception;

import lombok.Getter;

@Getter
public class TossPaymentApiException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;
    private final int httpStatus;
    private final String rawBody;

    public TossPaymentApiException(String errorCode, String errorMessage, int httpStatus, String rawBody) {
        super("토스 결제 API 오류: status=%s, code=%s, message=%s".formatted(httpStatus, errorCode, errorMessage));
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
        this.rawBody = rawBody;
    }
}
