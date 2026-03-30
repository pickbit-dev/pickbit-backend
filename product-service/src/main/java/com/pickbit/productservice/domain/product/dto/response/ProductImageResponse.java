package com.pickbit.productservice.domain.product.dto.response;

import com.pickbit.productservice.domain.product.entity.enums.ImageType;

public record ProductImageResponse(
        Long id,
        String imageUrl,
        ImageType imageType,
        Integer sortOrder
) {
}
