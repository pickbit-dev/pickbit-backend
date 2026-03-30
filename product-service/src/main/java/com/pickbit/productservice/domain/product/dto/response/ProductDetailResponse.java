package com.pickbit.productservice.domain.product.dto.response;

import com.pickbit.productservice.domain.product.entity.enums.ListingType;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        ProductStatus status,
        ProductCondition condition,
        ListingType listingType,
        Long categoryId,
        String categoryName,
        List<ProductImageResponse> images,
        LocalDateTime createdDate,
        LocalDateTime updatedDate
) {
}
