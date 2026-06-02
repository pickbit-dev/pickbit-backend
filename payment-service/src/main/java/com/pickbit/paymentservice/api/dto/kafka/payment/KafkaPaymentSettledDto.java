package com.pickbit.paymentservice.api.dto.kafka.payment;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record KafkaPaymentSettledDto(
        String eventId,
        Long paymentId,
        Long auctionId,
        Long productId,
        Long buyerUserId,
        Long sellerUserId,
        BigDecimal grossAmount,
        BigDecimal netSellerAmount,
        LocalDateTime releasedAt
) {
}
