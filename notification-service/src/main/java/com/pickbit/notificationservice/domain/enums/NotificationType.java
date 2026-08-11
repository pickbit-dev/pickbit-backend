package com.pickbit.notificationservice.domain.enums;

import lombok.Getter;

@Getter
public enum NotificationType {
    AUCTION_WON("경매 낙찰"),
    PAYMENT_ESCROWED("결제 완료"),
    PAYMENT_SETTLED("정산 완료"),
    PAYMENT_REFUNDED("환불 완료"),
    PAYMENT_FAILED_NO_PAYMENT("결제 기한 만료"),
    PAYMENT_CANCELLED_BEFORE_PAYMENT("결제 전 포기");

    private final String description;

    NotificationType(String description) {
        this.description = description;
    }
}
