package com.pickbit.productservice.application.event;

import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;

public record ProductStatusUpdateEvent(
        Long productId,
        ProductStatus status,
        String reason,
        Long auctionId
) {
}
