package com.pickbit.productservice.domain.product.entity.enums;

import lombok.Getter;

@Getter
public enum ListingType {
    NORMAL("일반 판매"),
    AUCTION("경매 판매");

    private final String description;

    ListingType(String description) {
        this.description = description;
    }
}
