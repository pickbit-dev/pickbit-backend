package com.pickbit.productservice.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record ScheduledAuctionResponse(
        Long auctionId,
        Long productId,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime startTime
) {
}
