package com.pickbit.auctionservice.api.dto.response;

import com.pickbit.auctionservice.domain.AuctionEvent;
import com.pickbit.auctionservice.domain.enums.AuctionEventType;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuctionEventResponse(
        Long eventId,
        AuctionEventType eventType,
        Long auctionId,
        Long bidId,
        AuctionStatus auctionStatus,
        BigDecimal currentPrice,
        String bidderNickname,
        LocalDateTime bidTime,
        String winnerNickname,
        BigDecimal finalPrice,
        LocalDateTime createdAt
) {
    public static AuctionEventResponse from(AuctionEvent event) {
        return new AuctionEventResponse(
                event.getId(),
                event.getEventType(),
                event.getAuction().getId(),
                event.getBidId(),
                event.getAuctionStatus(),
                event.getCurrentPrice(),
                event.getBidderNickname(),
                event.getBidTime(),
                event.getWinnerNickname(),
                event.getFinalPrice(),
                event.getCreatedDate()
        );
    }
}
