package com.pickbit.productservice.api.dto.response;

import com.pickbit.productservice.domain.product.entity.enums.ListingType;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품 목록 조회 응답 DTO.
 *
 * <p>상품 목록 화면에 필요한 요약 정보를 클라이언트에 반환합니다.
 *
 * @param id 상품 ID
 * @param name 상품명
 * @param price 상품 가격
 * @param productStatus 상품 판매 상태
 * @param productCondition 상품 상태
 * @param listingType 판매 방식
 * @param sellerNickname 판매자 닉네임
 * @param categoryName 카테고리명
 * @param thumbnailUrl 대표 썸네일 이미지 URL
 * @param createdAt 상품 등록 일시
 */
public record ProductSummaryResponse(
        Long id,
        String name,
        BigDecimal price,
        ProductStatus productStatus,
        ProductCondition productCondition,
        ListingType listingType,
        String sellerNickname,
        String categoryName,
        String thumbnailUrl,
        LocalDateTime createdAt
) {
}
