package com.pickbit.paymentservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * 결제 승인 요청입니다.
 *
 * @param paymentKey PG 결제 키
 * @param orderId PG 주문 ID
 * @param amount 승인할 결제 금액
 */
public record PaymentConfirmRequest(
        @NotBlank String paymentKey,
        @NotBlank String orderId,
        @NotNull @Positive BigDecimal amount
) {
}
