package com.pickbit.auctionservice.infrastructure.redis;

import java.math.BigDecimal;

/**
 * Lua 중재 결과입니다.
 *
 * @param accepted        입찰이 수락됐는지
 * @param reason          거절 사유 ({@code NOT_LOADED}, {@code NOT_ACTIVE}, {@code TIME_OVER},
 *                        {@code SELLER}, {@code TOO_LOW}) 또는 {@code OK}
 * @param minimumRequired {@code TOO_LOW} 일 때 필요한 최소 금액
 * @param sequence        수락된 경우 이 경매에서 이 이벤트의 순번
 * @param ended           즉시 구매가 도달로 경매가 함께 종료됐는지
 * @param endedSequence   종료 이벤트에 붙일 순번. 입찰 이벤트와 순번을 공유하면 누락 이벤트 복구가 깨진다
 */
public record BidArbitrationResult(
        boolean accepted,
        String reason,
        BigDecimal minimumRequired,
        long sequence,
        boolean ended,
        long endedSequence
) {
    public static final String NOT_LOADED = "NOT_LOADED";

    public boolean needsHydration() {
        return NOT_LOADED.equals(reason);
    }
}
