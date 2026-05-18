package com.pickbit.auctionservice.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WebSocketRedisSubscriber implements MessageListener {

    static final String TOPIC_PREFIX = "/topic/auctions/";
    static final String CHANNEL_PREFIX = "auction:ws:";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final long debounceMs;

    private final ConcurrentMap<Long, AuctionBidEvent> pending = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public WebSocketRedisSubscriber(
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            @Value("${auction.ws.debounce-ms:100}") long debounceMs) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.debounceMs = debounceMs;
    }

    @PostConstruct
    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ws-debounce-flush");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flushAll, debounceMs, debounceMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        flushAll();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            if (!channel.startsWith(CHANNEL_PREFIX)) {
                return;
            }
            Long auctionId = Long.parseLong(channel.substring(CHANNEL_PREFIX.length()));
            AuctionBidEvent event = objectMapper.readValue(message.getBody(), AuctionBidEvent.class);

            if (event.auctionStatus() == AuctionStatus.ENDED) {
                pending.remove(auctionId);
                sendLocal(auctionId, event);
                return;
            }

            pending.put(auctionId, event);
        } catch (Exception e) {
            log.error("WebSocket Redis 메시지 처리 실패", e);
        }
    }

    private void flushAll() {
        for (Long auctionId : new ArrayList<>(pending.keySet())) {
            AuctionBidEvent event = pending.remove(auctionId);
            if (event != null) {
                sendLocal(auctionId, event);
            }
        }
    }

    private void sendLocal(Long auctionId, AuctionBidEvent event) {
        messagingTemplate.convertAndSend(TOPIC_PREFIX + auctionId, event);
    }
}
