package com.pickbit.paymentservice.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentRequestInfoResponse(
        Long paymentId,
        String pgOrderId,
        BigDecimal amount,
        String orderName,
        String customerKey,
        String successUrl,
        String failUrl,
        LocalDateTime paymentDeadlineAt
) {
}
