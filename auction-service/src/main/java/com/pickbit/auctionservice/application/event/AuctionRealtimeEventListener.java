package com.pickbit.auctionservice.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRealtimeEventListener {

    static final String CHANNEL_PREFIX = "auction:ws:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(AuctionRealtimeEvent event) {
        try {
            String body = objectMapper.writeValueAsString(event.payload());
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + event.auctionId(), body);
        } catch (Exception e) {
            log.error("경매 WebSocket Redis 발행 실패. auctionId={}", event.auctionId(), e);
        }
    }
}
