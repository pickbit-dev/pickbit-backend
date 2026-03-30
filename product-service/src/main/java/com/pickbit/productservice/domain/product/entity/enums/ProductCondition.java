package com.pickbit.productservice.domain.product.entity.enums;

import lombok.Getter;

@Getter
public enum ProductCondition {
    NEW("새 상품"),
    LIKE_NEW("거의 새 상품"),
    GOOD("사용감 적음"),
    FAIR("사용감 있음"),
    POOR("사용감 많음");

    private final String description;

    ProductCondition(String description) {
        this.description = description;
    }
}
