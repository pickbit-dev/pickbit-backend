package com.pickbit.notificationservice.application.event;

import java.math.BigDecimal;

public record AuctionWonEvent(
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
