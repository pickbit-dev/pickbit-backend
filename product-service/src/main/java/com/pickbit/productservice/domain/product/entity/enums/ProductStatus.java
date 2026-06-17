package com.pickbit.productservice.domain.product.entity.enums;

import lombok.Getter;

@Getter
public enum ProductStatus {
    ACTIVE("활성화"),
    AUCTION_SCHEDULED("경매 예정"),
    IN_AUCTION("경매 중"),
    TRADE_IN_PROGRESS("거래 진행 중"),
    SOLD("판매 완료"),
    INACTIVE("비활성화"),
    AUCTION_COMPLETED("경매 종료"),
    DELETED("삭제됨");

    private final String description;

    ProductStatus(String description) {
        this.description = description;
    }
}
