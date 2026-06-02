package com.pickbit.productservice.application.event;

import java.time.LocalDateTime;

public record PaymentFailedNoPaymentEvent(
        String eventId,
        Long paymentId,
        Long auctionId,
        Long productId,
        Long buyerUserId,
        Long sellerUserId,
        LocalDateTime failedAt
) {
}
