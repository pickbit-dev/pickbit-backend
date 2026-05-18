package com.pickbit.auctionservice.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.config.TestContainerConfig;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.auctionservice.infrastructure.client.ProductServiceClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(TestContainerConfig.class)
@Testcontainers
@ActiveProfiles("test")
class WebSocketRedisSubscriberTest {

    private static final String CHANNEL_PREFIX = "auction:ws:";
    private static final String TOPIC_PREFIX = "/topic/auctions/";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    @BeforeEach
    void resetState() throws InterruptedException {
        // 이전 테스트에서 남았을 수 있는 debounce flush 흡수 후 mock 초기화
        Thread.sleep(100);
        reset(simpMessagingTemplate);
    }

    @Test
    @DisplayName("Redis 채널 발행 → 디바운스 후 로컬 SimpMessagingTemplate으로 전달")
    void publish_to_redis_is_bridged_to_local_broker() throws Exception {
        Long auctionId = 1L;
        AuctionBidEvent event = AuctionBidEvent.ofNewBid(
                BigDecimal.valueOf(100), "user1", LocalDateTime.now());

        stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + auctionId,
                objectMapper.writeValueAsString(event));

        ArgumentCaptor<AuctionBidEvent> captor = ArgumentCaptor.forClass(AuctionBidEvent.class);
        verify(simpMessagingTemplate, timeout(2000).times(1))
                .convertAndSend(eq(TOPIC_PREFIX + auctionId), captor.capture());
        Assertions.assertThat(captor.getValue().currentPrice())
                .isEqualByComparingTo(BigDecimal.valueOf(100));
        Assertions.assertThat(captor.getValue().auctionStatus())
                .isEqualTo(AuctionStatus.ACTIVE);
    }

    @Test
    @DisplayName("연속 ACTIVE 이벤트는 디바운스로 묶여 마지막 값만 송출")
    void rapid_active_events_are_debounced_to_last() throws Exception {
        Long auctionId = 2L;
        for (int i = 1; i <= 3; i++) {
            AuctionBidEvent event = AuctionBidEvent.ofNewBid(
                    BigDecimal.valueOf(i * 100), "user" + i, LocalDateTime.now());
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + auctionId,
                    objectMapper.writeValueAsString(event));
        }

        ArgumentCaptor<AuctionBidEvent> captor = ArgumentCaptor.forClass(AuctionBidEvent.class);
        verify(simpMessagingTemplate, timeout(2000).times(1))
                .convertAndSend(eq(TOPIC_PREFIX + auctionId), captor.capture());
        Assertions.assertThat(captor.getValue().currentPrice())
                .isEqualByComparingTo(BigDecimal.valueOf(300));
    }

    @Test
    @DisplayName("ENDED 이벤트는 디바운스를 우회하여 즉시 전송")
    void ended_event_bypasses_debounce() throws Exception {
        Long auctionId = 3L;

        AuctionBidEvent ended = AuctionBidEvent.ofEnded("winner", BigDecimal.valueOf(500));
        stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + auctionId,
                objectMapper.writeValueAsString(ended));

        verify(simpMessagingTemplate, timeout(2000).times(1))
                .convertAndSend(eq(TOPIC_PREFIX + auctionId),
                        argThat((Object payload) -> payload instanceof AuctionBidEvent
                                && ((AuctionBidEvent) payload).auctionStatus() == AuctionStatus.ENDED));
    }
}
