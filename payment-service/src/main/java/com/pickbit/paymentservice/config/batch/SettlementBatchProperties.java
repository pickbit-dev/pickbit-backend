package com.pickbit.paymentservice.config.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "payment.settlement-batch")
public record SettlementBatchProperties(
        int chunkSize,
        BigDecimal platformFeeRate,
        BigDecimal pgFeeRate
) {

    public SettlementBatchProperties {
        if (chunkSize <= 0) {
            chunkSize = 100;
        }
        if (platformFeeRate == null) {
            platformFeeRate = new BigDecimal("0.05");
        }
        if (pgFeeRate == null) {
            pgFeeRate = BigDecimal.ZERO;
        }
    }
}
