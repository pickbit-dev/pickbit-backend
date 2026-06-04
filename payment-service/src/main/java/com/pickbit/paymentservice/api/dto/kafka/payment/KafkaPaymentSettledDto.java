package com.pickbit.paymentservice.api.dto.kafka.payment;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정산 완료 Kafka 이벤트 payload입니다.
 *
 * @param eventId 이벤트 ID
 * @param paymentId 결제 ID
 * @param auctionId 경매 ID
 * @param productId 상품 ID
 * @param buyerUserId 구매자 사용자 ID
 * @param sellerUserId 판매자 사용자 ID
 * @param grossAmount 총 결제 금액
 * @param netSellerAmount 판매자 정산 금액
 * @param releasedAt 정산 완료 시각
 */
@Builder
public record KafkaPaymentSettledDto(
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
