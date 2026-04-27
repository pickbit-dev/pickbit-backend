package com.pickbit.productservice.api.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 상품 등록 요청 DTO.
 *
 * <p>{@code POST /products} 호출 시 신규 상품 등록을 위해 사용합니다.
 *
 * @param name 상품명
 * @param description 상품 설명
 * @param startingPrice 경매 시작가
 * @param productCondition 상품 컨디션
 * @param categoryId 소속 카테고리 ID (선택)
 * @param images 상품 이미지 목록
 */
public record ProductCreateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull @Positive BigDecimal startingPrice,
        @NotNull ProductCondition productCondition,
        Long categoryId,
        @NotEmpty @Valid List<ProductImageRequest> images
) {
}
