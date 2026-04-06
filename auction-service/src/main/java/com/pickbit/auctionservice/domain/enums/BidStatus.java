package com.pickbit.auctionservice.domain.enums;

import lombok.Getter;

@Getter
public enum BidStatus {
    ACTIVE("현재 최고 입찰"),
    OUTBID("더 높은 입찰에 의해 밀림"),
    WINNING("낙찰");

    private final String description;

    BidStatus(String description) {
        this.description = description;
    }
}
