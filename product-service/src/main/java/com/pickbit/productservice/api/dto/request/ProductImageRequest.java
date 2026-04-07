package com.pickbit.productservice.api.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ImageType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 상품 이미지 요청 DTO.
 *
 * <p>상품 등록 및 수정 요청에서 이미지 URL과 정렬 정보를 함께 전달할 때 사용합니다.
 *
 * @param imageUrl 상품 이미지 URL
 * @param imageType 이미지 유형
 * @param sortOrder 이미지 노출 순서
 */
public record ProductImageRequest(
        @NotBlank String imageUrl,
        @NotNull ImageType imageType,
        @NotNull @Min(0) Integer sortOrder
) {
}
