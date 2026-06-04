package com.pickbit.notificationservice.application.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 환불 완료 이벤트입니다.
 *
 * @param eventId 이벤트 ID
 * @param paymentId 결제 ID
 * @param auctionId 경매 ID
 * @param productId 상품 ID
 * @param buyerUserId 구매자 사용자 ID
 * @param sellerUserId 판매자 사용자 ID
 * @param amount 환불 금액
 * @param reason 환불 사유
 * @param refundedAt 환불 완료 시각
 */
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
