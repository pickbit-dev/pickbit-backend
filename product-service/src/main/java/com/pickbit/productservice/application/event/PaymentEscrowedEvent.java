package com.pickbit.productservice.application.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 완료 이벤트입니다.
 */
public record PaymentEscrowedEvent(
        String eventId,
        Long paymentId,
        Long auctionId,
        Long productId,
        Long buyerUserId,
        Long sellerUserId,
        BigDecimal amount,
        LocalDateTime paidAt,
        LocalDateTime confirmDeadlineAt
) {
}
