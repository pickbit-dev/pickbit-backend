package com.pickbit.auctionservice.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRealtimeEventListener {

    static final String CHANNEL_PREFIX = "auction:ws:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // fallbackExecution 이 반드시 필요하다. 중재 경로(BidCommandService.placeBidViaArbiter)는
    // 트랜잭션 없이 이벤트를 발행하는데, 기본값(false)이면 트랜잭션이 없을 때 리스너가
    // 조용히 실행되지 않는다 — 실시간 입찰이 아무에게도 전달되지 않았다.
    // 트랜잭션이 있는 폴백 경로에서는 종전대로 커밋 이후에 발행된다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void send(AuctionRealtimeEvent event) {
        try {
            String body = objectMapper.writeValueAsString(event.payload());
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + event.auctionId(), body);
        } catch (Exception e) {
            log.error("경매 WebSocket Redis 발행 실패. auctionId={}", event.auctionId(), e);
        }
    }
}
