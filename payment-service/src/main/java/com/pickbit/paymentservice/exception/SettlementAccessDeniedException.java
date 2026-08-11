package com.pickbit.paymentservice.exception;

public class SettlementAccessDeniedException extends RuntimeException {

    public SettlementAccessDeniedException() {
        super("해당 정산 내역에 접근할 권한이 없습니다.");
    }
}
