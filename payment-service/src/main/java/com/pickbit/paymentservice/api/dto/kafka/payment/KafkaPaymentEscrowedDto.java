package com.pickbit.paymentservice.api.dto.kafka.payment;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record KafkaPaymentEscrowedDto(
        String eventId,
        Long paymentId,
        Long auctionId,
        Long buyerUserId,
        Long sellerUserId,
        BigDecimal amount,
        LocalDateTime paidAt,
        LocalDateTime confirmDeadlineAt
) {
}
