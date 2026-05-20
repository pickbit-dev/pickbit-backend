package com.pickbit.productservice.api.dto.response;

/**
 * AI 상품 등록 추천 응답 DTO.
 *
 * @param categoryId 추천 카테고리 ID
 * @param categoryName 추천 카테고리명
 * @param description 추천 상품 설명
 */
public record ProductListingSuggestionResponse(
        Long categoryId,
        String categoryName,
        String description
) {
}
