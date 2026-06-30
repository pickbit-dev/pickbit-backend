package com.pickbit.paymentservice.application.settlement;

import com.pickbit.paymentservice.config.batch.SettlementBatchProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class SettlementAmountCalculator {

    private final SettlementBatchProperties properties;

    public SettlementAmountCalculator(SettlementBatchProperties properties) {
        this.properties = properties;
    }

    public SettlementAmounts calculate(BigDecimal grossAmount) {
        BigDecimal platformFeeAmount = calculateFee(grossAmount, properties.platformFeeRate());
        BigDecimal pgFeeAmount = calculateFee(grossAmount, properties.pgFeeRate());
        BigDecimal netSellerAmount = grossAmount.subtract(platformFeeAmount).subtract(pgFeeAmount)
                .setScale(2, RoundingMode.DOWN);
        return new SettlementAmounts(grossAmount.setScale(2, RoundingMode.DOWN), platformFeeAmount, pgFeeAmount, netSellerAmount);
    }

    private BigDecimal calculateFee(BigDecimal grossAmount, BigDecimal rate) {
        return grossAmount.multiply(rate).setScale(2, RoundingMode.DOWN);
    }

    public record SettlementAmounts(
            BigDecimal grossAmount,
            BigDecimal platformFeeAmount,
            BigDecimal pgFeeAmount,
            BigDecimal netSellerAmount
    ) {
    }
}
