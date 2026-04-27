package com.pickbit.productservice.domain.product.entity.enums;

import org.springframework.data.domain.Sort;

/**
 * 상품 목록 정렬 옵션.
 */
public enum ProductSort {
    LATEST,
    PRICE_ASC,
    PRICE_DESC;

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdDate");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "startingPrice");
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "startingPrice");
        };
    }
}
