package com.pickbit.auctionservice.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 금액을 Lua 로 넘기기 위한 정수 변환 검증.
 * 여기가 틀리면 입찰 금액이 조용히 어긋나므로 경계값을 확인한다.
 */
class MinorUnitsTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "1.00", "10000.00", "1234567.89", "99999999999.99"})
    @DisplayName("변환 후 되돌리면 원래 금액과 같다")
    void roundTrip(String raw) {
        BigDecimal amount = new BigDecimal(raw);

        assertThat(MinorUnits.toAmount(MinorUnits.toMinor(amount))).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("null 은 0으로 다룬다 (즉시 구매가 미설정)")
    void nullIsZero() {
        assertThat(MinorUnits.toMinor(null)).isZero();
    }

    @Test
    @DisplayName("원 단위 금액이 100배 정수가 된다")
    void scalesByHundred() {
        assertThat(MinorUnits.toMinor(new BigDecimal("10000.00"))).isEqualTo(1_000_000L);
        assertThat(MinorUnits.toMinor(new BigDecimal("0.01"))).isEqualTo(1L);
    }

    @Test
    @DisplayName("소수점 2자리를 넘는 금액은 조용히 반올림하지 않고 실패한다")
    void rejectsUnexpectedScale() {
        assertThatThrownBy(() -> MinorUnits.toMinor(new BigDecimal("1.005")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("Lua 가 정확히 다루는 범위(2^53) 안에 들어온다")
    void staysWithinLuaSafeInteger() {
        long maxSupported = MinorUnits.toMinor(new BigDecimal("99999999999.99"));

        assertThat(maxSupported).isLessThan(1L << 53);
    }
}
