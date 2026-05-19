package com.pickbit.paymentservice.exception;

import java.math.BigDecimal;

public class PaymentAmountMismatchException extends RuntimeException {

    public PaymentAmountMismatchException(BigDecimal expected, BigDecimal actual) {
        super("결제 금액이 일치하지 않습니다. expected=%s, actual=%s".formatted(expected, actual));
    }
}
