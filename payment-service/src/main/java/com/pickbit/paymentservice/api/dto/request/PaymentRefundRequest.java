package com.pickbit.paymentservice.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentRefundRequest(
        @NotBlank String reason
) {
}
