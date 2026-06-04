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

    @Transactional
    public AuctionBidEvent record(Auction auction, AuctionBidEvent payload) {
        AuctionEvent event = AuctionEvent.builder()
                .auction(auction)
                .eventType(AuctionEventType.valueOf(payload.eventType()))
                .bidId(payload.bidId())
                .auctionStatus(payload.auctionStatus())
                .currentPrice(payload.currentPrice())
                .bidderNickname(payload.bidderNickname())
                .bidTime(payload.bidTime())
                .winnerNickname(payload.winnerNickname())
                .finalPrice(payload.finalPrice())
                .build();
        AuctionEvent saved = auctionEventRepository.saveAndFlush(event);
        return payload.withEventMetadata(saved.getId(), saved.getCreatedDate());
    }
}
