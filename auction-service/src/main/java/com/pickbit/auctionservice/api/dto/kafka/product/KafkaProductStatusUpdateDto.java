package com.pickbit.auctionservice.api.dto.kafka.product;

import lombok.Builder;

@Builder
public record KafkaProductStatusUpdateDto(
        String eventId,
        Long productId,
        String status,
        String reason,
        Long auctionId
) {
}
