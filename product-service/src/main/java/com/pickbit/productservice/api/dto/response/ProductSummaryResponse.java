package com.pickbit.productservice.api.dto.response;

import com.pickbit.productservice.domain.product.entity.enums.ListingType;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductSummaryResponse(
        Long id,
        String name,
        BigDecimal price,
        ProductStatus productStatus,
        ProductCondition productCondition,
        ListingType listingType,
        String sellerNickname,
        String categoryName,
        String thumbnailUrl,
        LocalDateTime createdAt
) {
}
