package com.pickbit.paymentservice.api.dto.kafka.payment;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record KafkaPaymentRefundedDto(
        String eventId,
        Long paymentId,
        Long auctionId,
        Long productId,
        Long buyerUserId,
        Long sellerUserId,
        BigDecimal amount,
        String reason,
        LocalDateTime refundedAt
) {
}
