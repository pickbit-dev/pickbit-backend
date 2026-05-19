package com.pickbit.paymentservice.domain.enums;

import lombok.Getter;

@Getter
public enum PgProvider {
    TOSS_PAYMENTS("토스페이먼츠");

    private final String description;

    PgProvider(String description) {
        this.description = description;
    }
}
