package com.pickbit.paymentservice.domain.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    REQUESTED("결제 대기, PG confirm 전"),
    PG_PENDING("PG confirm 처리 중 또는 결과 확정 대기"),
    ESCROWED("결제 완료, 에스크로 보관 중"),
    PURCHASE_CONFIRMED("구매확정, 정산 대기"),
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
