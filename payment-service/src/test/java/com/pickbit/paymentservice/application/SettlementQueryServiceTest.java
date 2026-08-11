package com.pickbit.paymentservice.application;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.paymentservice.api.dto.response.SettlementResponse;
import com.pickbit.paymentservice.api.dto.response.SettlementSummaryResponse;
import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import com.pickbit.paymentservice.exception.SettlementAccessDeniedException;
import com.pickbit.paymentservice.exception.SettlementNotFoundException;
import com.pickbit.paymentservice.infrastructure.persistence.SettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    private static final Long SELLER_ID = 200L;

    @Mock
    SettlementRepository settlementRepository;

    @InjectMocks
    SettlementQueryService settlementQueryService;

    private static Settlement settlement(Long id, Long sellerUserId, SettlementStatus status) {
        Settlement settlement = Settlement.builder()
                .paymentId(id)
                .sellerUserId(sellerUserId)
                .productName("빈티지 카메라")
                .grossAmount(new BigDecimal("50000.00"))
                .platformFeeAmount(new BigDecimal("2500.00"))
                .pgFeeAmount(BigDecimal.ZERO)
                .netSellerAmount(new BigDecimal("47500.00"))
                .status(status)
                .build();
        ReflectionTestUtils.setField(settlement, "id", id);
        return settlement;
    }

    @Test
    @DisplayName("상태 필터가 없으면 전체 정산을 조회한다")
    void listsAllWhenNoStatusFilter() {
        Pageable pageable = PageRequest.of(0, 20);
        given(settlementRepository.findBySellerUserIdOrderByIdDesc(SELLER_ID, pageable))
                .willReturn(new PageImpl<>(List.of(settlement(1L, SELLER_ID, SettlementStatus.COMPLETED))));

        PageResponse<SettlementResponse> result =
                settlementQueryService.getMySettlements(SELLER_ID, null, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().netSellerAmount()).isEqualByComparingTo("47500.00");
        assertThat(result.content().getFirst().statusDescription()).isEqualTo("정산 완료");
    }

    @Test
    @DisplayName("상태 필터가 있으면 해당 상태만 조회한다")
    void listsByStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        given(settlementRepository.findBySellerUserIdAndStatusOrderByIdDesc(
                SELLER_ID, SettlementStatus.PENDING, pageable))
                .willReturn(new PageImpl<>(List.of(settlement(2L, SELLER_ID, SettlementStatus.PENDING))));

        PageResponse<SettlementResponse> result =
                settlementQueryService.getMySettlements(SELLER_ID, SettlementStatus.PENDING, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().status()).isEqualTo(SettlementStatus.PENDING);
        verify(settlementRepository).findBySellerUserIdAndStatusOrderByIdDesc(
                SELLER_ID, SettlementStatus.PENDING, pageable);
        verifyNoMoreInteractions(settlementRepository);
    }

    @Test
    @DisplayName("남의 정산은 조회할 수 없다")
    void cannotReadOthersSettlement() {
        given(settlementRepository.findById(1L))
                .willReturn(Optional.of(settlement(1L, 999L, SettlementStatus.COMPLETED)));

        assertThatThrownBy(() -> settlementQueryService.getSettlement(SELLER_ID, 1L))
                .isInstanceOf(SettlementAccessDeniedException.class);
    }

    @Test
    @DisplayName("없는 정산을 조회하면 예외가 발생한다")
    void missingSettlement() {
        given(settlementRepository.findById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> settlementQueryService.getSettlement(SELLER_ID, 42L))
                .isInstanceOf(SettlementNotFoundException.class);
    }

    @Test
    @DisplayName("요약은 상태별 건수와 합계를 돌려준다")
    void summarizes() {
        given(settlementRepository.summarizeBySeller(SELLER_ID)).willReturn(List.of(
                new Object[]{SettlementStatus.COMPLETED, 3L, new BigDecimal("142500.00")},
                new Object[]{SettlementStatus.PENDING, 1L, new BigDecimal("47500.00")}));

        SettlementSummaryResponse summary = settlementQueryService.getMySummary(SELLER_ID);

        assertThat(summary.completedCount()).isEqualTo(3);
        assertThat(summary.completedAmount()).isEqualByComparingTo("142500.00");
        assertThat(summary.pendingCount()).isEqualTo(1);
        assertThat(summary.pendingAmount()).isEqualByComparingTo("47500.00");
        // 집계 결과에 없는 상태는 0 으로 채운다.
        assertThat(summary.failedCount()).isZero();
        assertThat(summary.failedAmount()).isEqualByComparingTo("0");
    }
}
