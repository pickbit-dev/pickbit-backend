package com.pickbit.productservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 카테고리 등록 요청 DTO.
 *
 * @param name        카테고리명 (unique)
 * @param description 카테고리 설명
 * @param sortOrder   정렬 순서 (0 이상)
 */
public record CategoryCreateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull @PositiveOrZero Integer sortOrder
) {
}
