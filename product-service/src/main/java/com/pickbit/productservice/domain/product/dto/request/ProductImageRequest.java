package com.pickbit.productservice.domain.product.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ImageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductImageRequest(
        @NotBlank(message = "이미지 URL은 필수입니다.")
        String imageUrl,

        @NotNull(message = "이미지 타입은 필수입니다.")
        ImageType imageType,

        @NotNull(message = "이미지 정렬 순서는 필수입니다.")
        Integer sortOrder
) {
}
