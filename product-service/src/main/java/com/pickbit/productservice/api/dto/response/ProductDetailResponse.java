package com.pickbit.productservice.api.dto.response;

import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상품 상세 조회 응답 DTO.
 *
 * <p>상품 단건 조회 또는 등록, 수정 이후 클라이언트에 반환되는 상세 상품 정보입니다.
 *
 * @param id 상품 ID
 * @param name 상품명
 * @param description 상품 설명
 * @param startingPrice 경매 시작가
 * @param productStatus 상품 상태
 * @param productCondition 상품 컨디션
 * @param sellerNickname 판매자 닉네임
 * @param categoryId 카테고리 ID
 * @param categoryName 카테고리명
 * @param viewCount 조회수
 * @param images 상품 이미지 목록
 * @param createdAt 상품 생성 일시
 * @param updatedAt 상품 수정 일시
 */
public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        BigDecimal startingPrice,
        ProductStatus productStatus,
        ProductCondition productCondition,
        String sellerNickname,
        Long categoryId,
        String categoryName,
        Long viewCount,
        List<ProductImageResponse> images,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
