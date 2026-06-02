package com.pickbit.paymentservice.api.dto.response;

import com.pickbit.paymentservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentSummaryResponse(
        Long paymentId,
        Long auctionId,
        Long productId,
        String productName,
        String productThumbnailUrl,
        String sellerNickname,
        String buyerNickname,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime paymentDeadlineAt,
        LocalDateTime paidAt,
        LocalDateTime refundedAt
) {
}
