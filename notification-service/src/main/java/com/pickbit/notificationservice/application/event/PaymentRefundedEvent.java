package com.pickbit.notificationservice.application.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRefundedEvent(
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
