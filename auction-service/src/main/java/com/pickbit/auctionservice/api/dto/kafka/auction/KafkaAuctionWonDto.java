package com.pickbit.auctionservice.api.dto.kafka.auction;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * 경매 낙찰 완료 Kafka 이벤트 payload입니다.
 *
 * @param eventId 이벤트 ID
 * @param auctionId 경매 ID
 * @param productId 상품 ID
 * @param productName 상품명 스냅샷
 * @param productThumbnailUrl 상품 썸네일 URL 스냅샷
 * @param buyerUserId 낙찰자 사용자 ID
 * @param buyerNickname 낙찰자 닉네임
 * @param sellerUserId 판매자 사용자 ID
 * @param sellerNickname 판매자 닉네임
 * @param finalPrice 최종 낙찰가
 */
@Builder
public record KafkaAuctionWonDto(
        String eventId,
        Long auctionId,
        Long productId,
        String productName,
        String productThumbnailUrl,
        Long buyerUserId,
        String buyerNickname,
        Long sellerUserId,
        String sellerNickname,
        BigDecimal finalPrice
) {
}
