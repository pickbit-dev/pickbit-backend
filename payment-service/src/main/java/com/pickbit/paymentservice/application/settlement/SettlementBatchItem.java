package com.pickbit.paymentservice.application.settlement;

import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.Settlement;

public record SettlementBatchItem(
        Payment payment,
        Settlement settlement,
        boolean success,
        String failureReason
) {

    public static SettlementBatchItem success(Payment payment, Settlement settlement) {
        return new SettlementBatchItem(payment, settlement, true, null);
    }

    public static SettlementBatchItem failure(Settlement settlement, String failureReason) {
        return new SettlementBatchItem(null, settlement, false, failureReason);
    }
}
