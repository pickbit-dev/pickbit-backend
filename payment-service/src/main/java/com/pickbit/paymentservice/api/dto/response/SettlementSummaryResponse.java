package com.pickbit.paymentservice.api.dto.response;

import java.math.BigDecimal;

/**
 * 판매자 정산 요약입니다. 마이페이지 상단에 한 줄로 보여주기 위한 값입니다.
 *
 * @param pendingCount   정산 대기 건수
 * @param pendingAmount  정산 대기 금액 합계
 * @param completedCount 정산 완료 건수
 * @param completedAmount 정산 완료 금액 합계
 * @param failedCount    정산 실패 건수
 * @param failedAmount   정산 실패 금액 합계
 */
public record SettlementSummaryResponse(
        long pendingCount,
        BigDecimal pendingAmount,
        long completedCount,
        BigDecimal completedAmount,
        long failedCount,
        BigDecimal failedAmount
) {
}
