package com.pickbit.notificationservice.domain.enums;

import lombok.Getter;

@Getter
public enum NotificationTargetType {
    AUCTION("경매"),
    PAYMENT("결제"),
    PRODUCT("상품");

    private final String description;

    NotificationTargetType(String description) {
        this.description = description;
    }
}
