package com.pickbit.paymentservice.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

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
