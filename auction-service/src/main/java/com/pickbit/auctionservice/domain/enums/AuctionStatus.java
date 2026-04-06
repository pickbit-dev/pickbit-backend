package com.pickbit.auctionservice.domain.enums;

import lombok.Getter;

@Getter
public enum AuctionStatus {
    SCHEDULED("경매 예정"),
    ACTIVE("경매 진행 중"),
    ENDED("경매 종료"),
    CANCELLED("경매 취소");

    private final String description;

    AuctionStatus(String description) {
        this.description = description;
    }
}
