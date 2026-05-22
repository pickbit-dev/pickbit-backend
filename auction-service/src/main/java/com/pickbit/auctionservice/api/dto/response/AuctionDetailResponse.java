package com.pickbit.auctionservice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionDetailResponse(
        Long id,
        Long productId,
        String productName,
        String productThumbnailUrl,
        String sellerNickname,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal buyNowPrice,
        BigDecimal minimumBidIncrement,
        AuctionStatus auctionStatus,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime startTime,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime endTime,

        String winnerNickname,
        BigDecimal finalPrice,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime updatedAt
) {
}