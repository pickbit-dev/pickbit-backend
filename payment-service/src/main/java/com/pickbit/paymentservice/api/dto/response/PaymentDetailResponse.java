package com.pickbit.paymentservice.api.dto.response;

import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentDetailResponse(
        Long paymentId,
        Long auctionId,
        BigDecimal amount,
        PaymentStatus status,
        String pgOrderId,
        String pgPaymentKey,
        LocalDateTime paymentDeadlineAt,
        LocalDateTime paidAt,
        LocalDateTime refundedAt
) {
    public static PaymentDetailResponse from(Payment payment) {
        return new PaymentDetailResponse(
                payment.getId(),
                payment.getAuctionId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPgOrderId(),
                payment.getPgPaymentKey(),
                payment.getPaymentDeadlineAt(),
                payment.getPaidAt(),
                payment.getRefundedAt()
        );
    }
}
