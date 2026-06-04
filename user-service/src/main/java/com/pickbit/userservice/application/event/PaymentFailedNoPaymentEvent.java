package com.pickbit.userservice.application.event;

import java.time.LocalDateTime;

/**
 * 미결제로 인한 결제 실패 이벤트입니다.
 *
 * @param eventId 이벤트 ID
 * @param paymentId 결제 ID
 * @param auctionId 경매 ID
 * @param productId 상품 ID
 * @param buyerUserId 구매자 사용자 ID
 * @param sellerUserId 판매자 사용자 ID
 * @param failedAt 실패 처리 시각
 */
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
