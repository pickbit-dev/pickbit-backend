package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.AuctionEvent;
import com.pickbit.auctionservice.domain.enums.AuctionEventType;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuctionEventRecorder {

    private final AuctionEventRepository auctionEventRepository;
    private final AuctionSequenceAllocator sequenceAllocator;

    /**
     * 순번을 발급받아 이벤트를 저장합니다. 스케줄러가 만드는 시작/종료/취소 이벤트용입니다.
     * 입찰 이벤트는 Redis 가 이미 순번을 발급했으므로 {@link #record(Auction, AuctionBidEvent, long)}를 씁니다.
     */
    @Transactional
    public AuctionBidEvent record(Auction auction, AuctionBidEvent payload) {
        return record(auction, payload, sequenceAllocator.next(auction.getId()));
    }

    @Transactional
    public AuctionBidEvent record(Auction auction, AuctionBidEvent payload, long sequence) {
        AuctionEvent saved = auctionEventRepository.saveAndFlush(build(auction, payload, sequence));
        return payload.withEventMetadata(sequence, saved.getCreatedDate());
    }

    /**
     * 저장하지 않고 엔티티만 만듭니다. 영속화 워커가 여러 건을 모아 한 번에 기록할 때 사용합니다.
     */
    public AuctionEvent build(Auction auction, AuctionBidEvent payload, long sequence) {
        return AuctionEvent.builder()
                .auction(auction)
                .sequence(sequence)
                .eventType(AuctionEventType.valueOf(payload.eventType()))
                .bidId(payload.bidId())
                .auctionStatus(payload.auctionStatus())
                .currentPrice(payload.currentPrice())
                .bidderNickname(payload.bidderNickname())
                .bidTime(payload.bidTime())
                .winnerNickname(payload.winnerNickname())
                .finalPrice(payload.finalPrice())
                .build();
    }
}
