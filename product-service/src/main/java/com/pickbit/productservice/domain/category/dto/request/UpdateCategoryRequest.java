package com.pickbit.productservice.domain.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank(message = "카테고리명은 필수입니다.")
        String name,

        @NotBlank(message = "설명은 필수입니다.")
        @Size(max = 500, message = "설명은 500자를 초과할 수 없습니다.")
        String description,

        @NotNull(message = "활성화 여부는 필수입니다.")
        Boolean active,

        @NotNull(message = "정렬 순서는 필수입니다.")
        Integer sortOrder,

        Long parentCategoryId
) {
}
