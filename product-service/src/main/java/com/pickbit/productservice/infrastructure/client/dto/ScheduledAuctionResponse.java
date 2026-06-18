package com.pickbit.productservice.infrastructure.client.dto;

import java.time.LocalDateTime;

public record ScheduledAuctionResponse(
        Long auctionId,
        Long productId,
        LocalDateTime startTime
) {
}
