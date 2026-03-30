package com.pickbit.productservice.domain.product.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ListingType;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record UpdateProductRequest(
        @NotBlank(message = "상품명은 필수입니다.")
        String name,

        @NotBlank(message = "설명은 필수입니다.")
        String description,

        @NotNull(message = "가격은 필수입니다.")
        @DecimalMin(value = "0.0", inclusive = false, message = "가격은 0보다 커야 합니다.")
        BigDecimal price,

        @NotNull(message = "상품 상태는 필수입니다.")
        ProductStatus status,

        @NotNull(message = "상품 컨디션은 필수입니다.")
        ProductCondition condition,

        @NotNull(message = "판매 타입은 필수입니다.")
        ListingType listingType,

        @NotNull(message = "카테고리 ID는 필수입니다.")
        Long categoryId,

        @Valid
        List<ProductImageRequest> images
) {
}
