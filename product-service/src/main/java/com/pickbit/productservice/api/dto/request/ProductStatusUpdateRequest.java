package com.pickbit.productservice.api.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 상품 상태 변경 요청 DTO (내부 서비스 전용).
 *
 * <p>auction-service 등 내부 서비스에서 {@code PATCH /internal/products/{id}/status} 호출 시 사용합니다.
 *
 * @param status 변경할 상품 상태
 */
public record ProductStatusUpdateRequest(
        @NotNull(message = "상태는 필수입니다.")
        ProductStatus status
) {
}
