package com.pickbit.paymentservice.domain;

import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementTest {

    private static Settlement pending(Long sellerUserId) {
        return Settlement.builder()
                .paymentId(1L)
                .sellerUserId(sellerUserId)
                .grossAmount(new BigDecimal("50000.00"))
                .platformFeeAmount(BigDecimal.ZERO)
                .pgFeeAmount(BigDecimal.ZERO)
                .netSellerAmount(new BigDecimal("50000.00"))
                .status(SettlementStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("판매자 본인만 소유자로 인정한다")
    void ownership() {
        Settlement settlement = pending(200L);

        assertThat(settlement.isOwnedBy(200L)).isTrue();
        assertThat(settlement.isOwnedBy(999L)).isFalse();
        assertThat(settlement.isOwnedBy(null)).isFalse();
    }

    @Test
    @DisplayName("판매자가 없는 정산은 누구의 것도 아니다")
    void ownershipWithoutSeller() {
        assertThat(pending(null).isOwnedBy(200L)).isFalse();
    }

    @Test
    @DisplayName("실패할 때마다 재시도 횟수가 올라간다")
    void failureIncrementsRetryCount() {
        Settlement settlement = pending(200L);
        assertThat(settlement.getRetryCount()).isZero();

        settlement.markFailed("PG 오류");
        assertThat(settlement.getRetryCount()).isEqualTo(1);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FAILED);

        settlement.markFailed("PG 오류");
        // 상한을 두려면 횟수가 실제로 누적되어야 한다. 예전에는 FAILED 가 아예 재시도되지 않았다.
        assertThat(settlement.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("실패했다가 완료되면 실패 사유가 지워진다")
    void completionClearsFailureReason() {
        Settlement settlement = pending(200L);
        settlement.markFailed("일시적 오류");

        settlement.markCompleted(LocalDateTime.now());

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(settlement.getFailureReason()).isNull();
        assertThat(settlement.getSettledAt()).isNotNull();
    }

    @Test
    @DisplayName("완료된 정산의 금액은 다시 계산되지 않는다")
    void completedAmountsAreImmutable() {
        Settlement settlement = pending(200L);
        settlement.markCompleted(LocalDateTime.now());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> settlement.applyAmounts(
                        new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1.00")))
                .isInstanceOf(IllegalStateException.class);
    }
}
