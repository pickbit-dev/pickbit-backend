package com.pickbit.productservice.api.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import jakarta.validation.constraints.NotBlank;

/**
 * AI 상품 등록 추천 요청 DTO.
 *
 * @param title 상품 제목 또는 상품명
 * @param memo 상품 상태, 구성품 등 사용자가 입력한 간단 메모
 * @param productCondition 상품 컨디션
 */
public record ProductListingSuggestionRequest(
        @NotBlank String title,
        String memo,
        ProductCondition productCondition
) {
}
