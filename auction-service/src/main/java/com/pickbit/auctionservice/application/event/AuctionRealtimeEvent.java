package com.pickbit.auctionservice.application.event;

import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;

public record AuctionRealtimeEvent(
        Long auctionId,
        AuctionBidEvent payload
) {
}
