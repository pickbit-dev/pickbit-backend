package com.pickbit.productservice.api.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ListingType;
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
 * <p>{@code POST /products} 호출 시 신규 상품 등록에 필요한 정보를 전달합니다.
 *
 * @param name 상품명
 * @param description 상품 설명
 * @param price 상품 가격
 * @param productCondition 상품 상태
 * @param listingType 판매 방식
 * @param categoryId 소속 카테고리 ID
 * @param images 상품 이미지 목록
 */
public record ProductCreateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull @Positive BigDecimal price,
        @NotNull ProductCondition productCondition,
        @NotNull ListingType listingType,
        @NotNull Long categoryId,
        @NotEmpty @Valid List<ProductImageRequest> images
) {
}
