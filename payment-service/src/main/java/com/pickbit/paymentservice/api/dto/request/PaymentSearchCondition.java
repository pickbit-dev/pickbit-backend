package com.pickbit.paymentservice.api.dto.request;

import com.pickbit.paymentservice.domain.enums.PaymentStatus;

/**
 * 결제 목록 검색 조건입니다.
 *
 * @param paymentType 결제 조회 관점
 * @param status 결제 상태
 */
public record PaymentSearchCondition(
        PaymentViewType paymentType,
        PaymentStatus status
) {
}
