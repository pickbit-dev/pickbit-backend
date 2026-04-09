package com.pickbit.productservice.api.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ListingType;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * 상품 수정 요청 DTO.
 *
 * <p>{@code PUT /products/{id}} 호출 시 기존 상품 정보를 수정하기 위해 사용합니다.
 *
 * @param name 상품명
 * @param description 상품 설명
 * @param startingPrice 상품 가격
 * @param productStatus 상품 판매 상태
 * @param productCondition 상품 상태
 * @param listingType 판매 방식
 * @param categoryId 소속 카테고리 ID
 * @param images 상품 이미지 목록
 */
public record ProductUpdateRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotNull @Positive BigDecimal startingPrice,
        @NotNull ProductStatus productStatus,
        @NotNull ProductCondition productCondition,
        @NotNull ListingType listingType,
        @NotNull Long categoryId,
        @NotEmpty @Valid List<ProductImageRequest> images
) {
}
