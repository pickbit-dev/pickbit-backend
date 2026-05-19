package com.pickbit.paymentservice.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long paymentId) {
        super("결제를 찾을 수 없습니다. id=" + paymentId);
    }

    public PaymentNotFoundException(String pgOrderId) {
        super("결제를 찾을 수 없습니다. pgOrderId=" + pgOrderId);
    }
}
