package com.pickbit.productservice.domain.category.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateCategoryActiveRequest(
        @NotNull(message = "활성화 여부는 필수입니다.")
        Boolean active
) {
}
