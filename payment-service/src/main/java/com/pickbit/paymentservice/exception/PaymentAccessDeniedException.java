package com.pickbit.paymentservice.exception;

public class PaymentAccessDeniedException extends RuntimeException {

    public PaymentAccessDeniedException() {
        super("해당 결제에 접근할 권한이 없습니다.");
    }
}
