package com.pickbit.productservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 카테고리 수정 요청 DTO.
 *
 * @param name        카테고리명
 * @param description 카테고리 설명
 * @param sortOrder   정렬 순서
 */
public record CategoryUpdateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull @PositiveOrZero Integer sortOrder
) {
}
