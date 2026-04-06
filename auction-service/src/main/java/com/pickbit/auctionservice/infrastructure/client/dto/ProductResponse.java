package com.pickbit.auctionservice.infrastructure.client.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String productStatus,
        String productCondition,
        String listingType,
        String sellerNickname,
        Long categoryId,
        String categoryName,
        List<ProductImageResponse> images
) {
    public String thumbnailUrl() {
        if (images == null) return null;
        return images.stream()
                .filter(img -> "THUMBNAIL".equals(img.imageType()))
                .findFirst()
                .map(ProductImageResponse::imageUrl)
                .orElse(null);
    }

    public record ProductImageResponse(
            Long id,
            String imageUrl,
            String imageType,
            Integer sortOrder
    ) {}
}
