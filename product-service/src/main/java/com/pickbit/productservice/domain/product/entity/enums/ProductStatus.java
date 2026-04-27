package com.pickbit.productservice.domain.product.entity.enums;

import lombok.Getter;

@Getter
public enum ProductStatus {
    ACTIVE("판매 중"),
    INACTIVE("비활성화"),
    AUCTION_COMPLETED("경매 종료"),
    DELETED("삭제됨");

    private final String description;

    ProductStatus(String description) {
        this.description = description;
    }
}
