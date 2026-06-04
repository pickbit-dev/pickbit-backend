package com.pickbit.productservice.application.event;

import java.time.LocalDateTime;

/**
 * 결제 전 포기 이벤트입니다.
 *
 * @param eventId 이벤트 ID
 * @param paymentId 결제 ID
 * @param auctionId 경매 ID
 * @param productId 상품 ID
 * @param buyerUserId 구매자 사용자 ID
 * @param sellerUserId 판매자 사용자 ID
 * @param cancelledAt 결제 포기 처리 시각
 */
public record PaymentCancelledBeforePaymentEvent(
        String eventId,
        Long paymentId,
        Long auctionId,
        Long productId,
        Long buyerUserId,
        Long sellerUserId,
        LocalDateTime cancelledAt
) {
}
