package com.pickbit.productservice.application.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정산 완료 이벤트입니다.
 */
public record PaymentSettledEvent(
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
