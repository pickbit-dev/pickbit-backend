package com.pickbit.productservice.application.event;

import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;

/**
 * 상품 상태 변경 이벤트입니다.
 *
 * @param eventId 이벤트 ID
 * @param productId 상품 ID
 * @param status 변경할 상품 상태
 * @param reason 상태 변경 사유
 * @param auctionId 상태 변경을 발생시킨 경매 ID
 */
public record ProductStatusUpdateEvent(
        String eventId,
        Long productId,
        ProductStatus status,
        String reason,
        Long auctionId
) {
}
