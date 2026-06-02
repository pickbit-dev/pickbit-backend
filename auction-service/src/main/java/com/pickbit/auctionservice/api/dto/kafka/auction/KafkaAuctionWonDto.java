package com.pickbit.auctionservice.api.dto.kafka.auction;

import lombok.Builder;

import java.math.BigDecimal;

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
