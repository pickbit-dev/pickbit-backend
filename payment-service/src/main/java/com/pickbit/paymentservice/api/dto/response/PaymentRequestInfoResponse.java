package com.pickbit.paymentservice.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 요청 생성 정보 응답입니다.
 *
 * @param paymentId 결제 ID
 * @param pgOrderId PG 주문 ID
 * @param amount 결제 금액
 * @param orderName 주문명
 * @param customerKey 고객 식별 키
 * @param successUrl 결제 성공 리다이렉트 URL
 * @param failUrl 결제 실패 리다이렉트 URL
 * @param paymentDeadlineAt 결제 기한
 */
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
