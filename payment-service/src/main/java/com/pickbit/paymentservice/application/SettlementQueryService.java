package com.pickbit.paymentservice.application;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.paymentservice.api.dto.response.SettlementResponse;
import com.pickbit.paymentservice.api.dto.response.SettlementSummaryResponse;
import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import com.pickbit.paymentservice.exception.SettlementAccessDeniedException;
import com.pickbit.paymentservice.exception.SettlementNotFoundException;
import com.pickbit.paymentservice.infrastructure.persistence.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 판매자 정산 내역 조회입니다.
 *
 * <p>배치가 계산한 정산 행을 읽을 수 있는 경로가 아예 없었습니다.
 * 판매자는 얼마를 언제 받는지 확인할 방법이 없었습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementQueryService {

    private final SettlementRepository settlementRepository;

    public PageResponse<SettlementResponse> getMySettlements(
            Long sellerUserId, SettlementStatus status, Pageable pageable) {

        Page<Settlement> page = status == null
                ? settlementRepository.findBySellerUserIdOrderByIdDesc(sellerUserId, pageable)
                : settlementRepository.findBySellerUserIdAndStatusOrderByIdDesc(sellerUserId, status, pageable);

        return PageResponse.from(page.map(SettlementResponse::from));
    }

    /**
     * 정산 상세를 조회합니다. 본인 정산만 볼 수 있습니다.
     */
    public SettlementResponse getSettlement(Long sellerUserId, Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new SettlementNotFoundException(settlementId));
        if (!settlement.isOwnedBy(sellerUserId)) {
            throw new SettlementAccessDeniedException();
        }
        return SettlementResponse.from(settlement);
    }

    /**
     * 상태별 건수와 금액 합계를 돌려줍니다.
     */
    public SettlementSummaryResponse getMySummary(Long sellerUserId) {
        Map<SettlementStatus, long[]> counts = new EnumMap<>(SettlementStatus.class);
        Map<SettlementStatus, BigDecimal> amounts = new EnumMap<>(SettlementStatus.class);

        List<Object[]> rows = settlementRepository.summarizeBySeller(sellerUserId);
        for (Object[] row : rows) {
            SettlementStatus status = (SettlementStatus) row[0];
            counts.put(status, new long[]{((Number) row[1]).longValue()});
            amounts.put(status, (BigDecimal) row[2]);
        }

        return new SettlementSummaryResponse(
                count(counts, SettlementStatus.PENDING),
                amount(amounts, SettlementStatus.PENDING),
                count(counts, SettlementStatus.COMPLETED),
                amount(amounts, SettlementStatus.COMPLETED),
                count(counts, SettlementStatus.FAILED),
                amount(amounts, SettlementStatus.FAILED));
    }

    private static long count(Map<SettlementStatus, long[]> counts, SettlementStatus status) {
        long[] value = counts.get(status);
        return value == null ? 0L : value[0];
    }

    private static BigDecimal amount(Map<SettlementStatus, BigDecimal> amounts, SettlementStatus status) {
        return amounts.getOrDefault(status, BigDecimal.ZERO);
    }
}
