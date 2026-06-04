package com.pickbit.notificationservice.application.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 에스크로 완료 이벤트입니다.
 *
 * @param eventId 이벤트 ID
 * @param paymentId 결제 ID
 * @param auctionId 경매 ID
 * @param productId 상품 ID
 * @param buyerUserId 구매자 사용자 ID
 * @param sellerUserId 판매자 사용자 ID
 * @param amount 결제 금액
 * @param paidAt 결제 완료 시각
 * @param confirmDeadlineAt 구매 확정 기한
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
