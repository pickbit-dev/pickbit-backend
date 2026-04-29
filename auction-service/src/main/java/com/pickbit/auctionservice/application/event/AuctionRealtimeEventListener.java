package com.pickbit.auctionservice.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRealtimeEventListener {

    private static final String AUCTION_TOPIC = "/topic/auctions/";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(AuctionRealtimeEvent event) {
        try {
            messagingTemplate.convertAndSend(AUCTION_TOPIC + event.auctionId(), event.payload());
        } catch (Exception e) {
            log.error("경매 WebSocket 알림 실패. auctionId={}", event.auctionId(), e);
        }
    }
}
