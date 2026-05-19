package com.pickbit.paymentservice.domain.enums;

import lombok.Getter;

@Getter
public enum SettlementStatus {
    PENDING("정산 대기"),
    COMPLETED("정산 완료"),
    FAILED("정산 실패 (재시도 필요)");

    private final String description;

    SettlementStatus(String description) {
        this.description = description;
    }
}
