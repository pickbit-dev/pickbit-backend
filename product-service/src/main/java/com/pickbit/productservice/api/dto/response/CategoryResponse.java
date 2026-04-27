package com.pickbit.productservice.api.dto.response;

/**
 * 카테고리 응답 DTO.
 *
 * @param id          카테고리 ID
 * @param name        카테고리명
 * @param description 카테고리 설명
 * @param active      활성화 여부
 * @param sortOrder   정렬 순서
 */
public record CategoryResponse(
        Long id,
        String name,
        String description,
        boolean active,
        Integer sortOrder
) {
}
