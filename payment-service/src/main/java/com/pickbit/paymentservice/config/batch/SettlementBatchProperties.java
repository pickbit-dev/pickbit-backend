package com.pickbit.paymentservice.config.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "payment.settlement-batch")
public record SettlementBatchProperties(
        int chunkSize,
        BigDecimal platformFeeRate,
        BigDecimal pgFeeRate,
        /**
         * 실패한 정산의 최대 재시도 횟수.
         * 이 횟수를 넘으면 배치가 더 이상 집어가지 않고 사람이 확인해야 하는 상태로 남습니다.
         */
        int maxRetries
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
        if (maxRetries <= 0) {
            maxRetries = 5;
        }
    }
}
