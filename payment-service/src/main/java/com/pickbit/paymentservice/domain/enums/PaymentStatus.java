package com.pickbit.paymentservice.domain.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    REQUESTED("결제 요청됨 (낙찰 직후)"),
    PG_PENDING("PG 결제창 진행 중"),
    ESCROWED("결제 완료, 에스크로 보관 중"),
    RELEASED("판매자에게 정산 완료"),
    REFUNDED("환불 완료"),
    FAILED("결제 실패"),
    CANCELLED("결제 취소"),
    DISPUTED("분쟁 진행 중");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }
}
