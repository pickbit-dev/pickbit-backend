package com.pickbit.auctionservice.api.dto.kafka.product;

import lombok.Builder;

/**
 * 상품 상태 변경 Kafka 이벤트 payload입니다.
 *
 * @param eventId 이벤트 ID
 * @param productId 상품 ID
 * @param status 변경할 상품 상태
 * @param reason 상태 변경 사유
 * @param auctionId 상태 변경을 발생시킨 경매 ID
 */
@Builder
public record KafkaProductStatusUpdateDto(
        String eventId,
        Long productId,
        String status,
        String reason,
        Long auctionId
) {
}
