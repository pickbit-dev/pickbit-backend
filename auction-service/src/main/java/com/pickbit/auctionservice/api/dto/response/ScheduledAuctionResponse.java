package com.pickbit.auctionservice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 예정 경매 조회 응답입니다.
 *
 * @param auctionId 경매 ID
 * @param productId 상품 ID
 * @param startTime 경매 시작 시각
 */
public record ScheduledAuctionResponse(
        Long auctionId,
        Long productId,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime startTime
) {
}
