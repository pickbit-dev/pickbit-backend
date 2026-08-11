package com.pickbit.auctionservice.infrastructure.redis;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 금액을 minor unit(원 * 100) 정수로 변환합니다.
 *
 * <p>Lua 는 실수 연산만 제공하므로 금액을 그대로 넘기면 비교와 덧셈에 오차가 생길 수 있습니다.
 * 스키마상 금액은 {@code precision 19, scale 2} 이므로 100을 곱하면 항상 정확한 정수가 되고,
 * Lua 가 정확히 다룰 수 있는 범위(2^53) 안에 충분히 들어옵니다.
 */
public final class MinorUnits {

    private static final int SCALE = 2;
    private static final BigDecimal FACTOR = BigDecimal.valueOf(100);

    private MinorUnits() {
    }

    public static long toMinor(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY)
                .multiply(FACTOR)
                .longValueExact();
    }

    public static BigDecimal toAmount(long minor) {
        return BigDecimal.valueOf(minor).divide(FACTOR, SCALE, RoundingMode.UNNECESSARY);
    }
}
