package com.pickbit.paymentservice.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 토스페이먼츠 결제 응답입니다.
 *
 * @param paymentKey 결제 키
 * @param orderId 주문 ID
 * @param status 결제 상태
 * @param totalAmount 총 결제 금액
 * @param balanceAmount 취소 가능 잔액
 * @param method 결제 수단
 * @param approvedAt 승인 시각
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        BigDecimal totalAmount,
        BigDecimal balanceAmount,
        String method,
        String approvedAt
) {
}
