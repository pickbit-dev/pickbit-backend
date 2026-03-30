package com.pickbit.productservice.domain.product.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateProductStatusRequest(
        @NotNull(message = "상품 상태는 필수입니다.")
        ProductStatus status
) {
}
