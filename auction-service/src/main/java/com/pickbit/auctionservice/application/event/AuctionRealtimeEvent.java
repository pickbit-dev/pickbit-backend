package com.pickbit.auctionservice.application.event;

import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;

/**
 * 경매 실시간 메시지 발행을 요청하는 애플리케이션 이벤트입니다.
 *
 * @param auctionId 메시지를 발행할 경매 ID
 * @param payload WebSocket으로 전송할 경매 이벤트 payload
 */
public record AuctionRealtimeEvent(
        Long auctionId,
        AuctionBidEvent payload
) {
}
