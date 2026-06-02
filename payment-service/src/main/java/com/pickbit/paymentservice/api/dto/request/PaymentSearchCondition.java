package com.pickbit.paymentservice.api.dto.request;

import com.pickbit.paymentservice.domain.enums.PaymentStatus;

public record PaymentSearchCondition(
        PaymentViewType paymentType,
        PaymentStatus status
) {
}
