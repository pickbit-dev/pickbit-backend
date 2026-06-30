package com.pickbit.paymentservice.application.settlement;

import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementBatchProcessor implements ItemProcessor<Settlement, SettlementBatchItem> {

    private final PaymentRepository paymentRepository;
    private final SettlementAmountCalculator amountCalculator;

    @Override
    public SettlementBatchItem process(Settlement settlement) {
        return paymentRepository.findByIdForUpdate(settlement.getPaymentId())
                .map(payment -> process(payment, settlement))
                .orElseGet(() -> SettlementBatchItem.failure(settlement, "정산 대상 결제를 찾을 수 없습니다. paymentId=" + settlement.getPaymentId()));
    }

    private SettlementBatchItem process(Payment payment, Settlement settlement) {
        if (payment.getStatus() != PaymentStatus.PURCHASE_CONFIRMED) {
            return SettlementBatchItem.failure(settlement, "정산 가능한 결제 상태가 아닙니다. status=" + payment.getStatus());
        }

        SettlementAmountCalculator.SettlementAmounts amounts = amountCalculator.calculate(payment.getAmount());
        settlement.applyAmounts(
                amounts.grossAmount(),
                amounts.platformFeeAmount(),
                amounts.pgFeeAmount(),
                amounts.netSellerAmount()
        );
        return SettlementBatchItem.success(payment, settlement);
    }
}
