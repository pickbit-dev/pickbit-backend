package com.pickbit.auctionservice.infrastructure.redis;

import java.math.BigDecimal;

/**
 * Redis 에 올라와 있는 경매의 현재 상태입니다.
 *
 * @param status       ACTIVE / ENDED 등
 * @param currentPrice 현재 최고가
 * @param hasBid       입찰이 한 건이라도 있는지
 * @param sequence     지금까지 발급된 마지막 순번
 * @param persistedSeq 영속화 워커가 DB에 반영 완료한 마지막 순번
 */
public record AuctionState(
        String status,
        BigDecimal currentPrice,
        boolean hasBid,
        long sequence,
        long persistedSeq
) {
    public boolean isFullyPersisted() {
        return persistedSeq >= sequence;
    }
}
