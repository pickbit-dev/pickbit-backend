package com.pickbit.paymentservice.exception;

public class SettlementNotFoundException extends RuntimeException {

    public SettlementNotFoundException(Long settlementId) {
        super("정산 내역을 찾을 수 없습니다. id=" + settlementId);
    }
}
