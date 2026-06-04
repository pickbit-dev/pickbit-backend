package com.pickbit.auctionservice.application.event;

import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class WebSocketRedisSubscriber implements MessageListener {

    static final String TOPIC_PREFIX = "/topic/auctions/";
    static final String CHANNEL_PREFIX = "auction:ws:";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public WebSocketRedisSubscriber(
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
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
            messagingTemplate.convertAndSend(TOPIC_PREFIX + auctionId, event);
        } catch (Exception e) {
            log.error("WebSocket Redis 메시지 처리 실패", e);
        }
    }
}
