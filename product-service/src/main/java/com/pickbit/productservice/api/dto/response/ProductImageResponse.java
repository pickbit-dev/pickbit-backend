package com.pickbit.productservice.api.dto.response;

import com.pickbit.productservice.domain.product.entity.enums.ImageType;

/**
 * 상품 이미지 응답 DTO.
 *
 * <p>상품 상세 정보에 포함되는 개별 이미지 메타데이터를 나타냅니다.
 *
 * @param id 이미지 ID
 * @param imageUrl 상품 이미지 URL
 * @param imageType 이미지 유형
 * @param sortOrder 이미지 노출 순서
 */
public record ProductImageResponse(
        Long id,
        String imageUrl,
        ImageType imageType,
        Integer sortOrder
) {
}
