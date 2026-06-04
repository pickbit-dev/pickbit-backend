package com.pickbit.paymentservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 결제 환불 요청입니다.
 *
 * @param reason 환불 사유
 */
public record PaymentRefundRequest(
        @NotBlank String reason
) {
}
