package com.pickbit.productservice.domain.product.entity.enums;

import lombok.Getter;

@Getter
public enum ImageType {
    THUMBNAIL("썸네일 이미지"),
    DETAIL("상세 이미지");

    private final String description;

    ImageType(String description) {
        this.description = description;
    }
}
