package com.pickbit.paymentservice.api.dto.kafka.payment;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record KafkaPaymentFailedNoPaymentDto(
        String eventId,
        Long paymentId,
        Long auctionId,
        Long buyerUserId,
        Long sellerUserId,
        LocalDateTime failedAt
) {
}
