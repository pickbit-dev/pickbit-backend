package com.pickbit.notificationservice.application.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정산 완료 이벤트입니다.
 *
 * <p>payment-service 의 {@code KafkaPaymentSettledDto} 와 필드가 1:1로 대응해야 합니다.
 *
 * @param eventId 이벤트 ID
 * @param paymentId 결제 ID
 * @param auctionId 경매 ID
 * @param productId 상품 ID
 * @param buyerUserId 구매자 사용자 ID
 * @param sellerUserId 판매자 사용자 ID
 * @param grossAmount 총 결제 금액
 * @param netSellerAmount 판매자 정산 금액 (수수료를 뺀 실수령액)
 * @param releasedAt 정산 완료 시각
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
