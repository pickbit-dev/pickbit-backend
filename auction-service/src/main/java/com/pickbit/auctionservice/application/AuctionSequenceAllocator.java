package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.config.BidArbiterProperties;
import com.pickbit.auctionservice.domain.AuctionEvent;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionEventRepository;
import com.pickbit.auctionservice.infrastructure.redis.AuctionState;
import com.pickbit.auctionservice.infrastructure.redis.AuctionStateStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 경매 이벤트 순번을 발급합니다.
 *
 * <p>중재가 켜져 있으면 Redis 가 순번의 진실 원천입니다. 입찰은 Lua 스크립트 안에서 순번을 받고,
 * 시작/종료/취소 같은 스케줄러 이벤트는 여기서 같은 카운터를 증가시켜 서로 뒤섞이지 않게 합니다.
 *
 * <p>중재가 꺼져 있거나 Redis 상태가 아직 없으면 DB 의 마지막 순번에서 이어갑니다.
 * 이 경로는 경매 락 안에서만 호출되므로 경합이 없습니다.
 */
@Component
@RequiredArgsConstructor
public class AuctionSequenceAllocator {

    private final BidArbiterProperties properties;
    private final AuctionStateStore stateStore;
    private final AuctionEventRepository auctionEventRepository;

    public long next(Long auctionId) {
        if (properties.isEnabled()) {
            AuctionState state = stateStore.read(auctionId);
            if (state != null) {
                return stateStore.nextSequence(auctionId);
            }
        }
        return lastPersistedSequence(auctionId) + 1;
    }

    /** DB에 기록된 마지막 순번. Redis 상태를 복구할 때 이어갈 지점이기도 하다. */
    public long lastPersistedSequence(Long auctionId) {
        return auctionEventRepository.findTopByAuctionIdOrderBySequenceDesc(auctionId)
                .map(AuctionEvent::getSequence)
                .orElse(0L);
    }
}
