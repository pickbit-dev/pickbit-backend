package com.pickbit.auctionservice.application.event;

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
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 다른 테스트 클래스가 만드는 경매는 auto-increment 라 1, 2, 3 부터 시작한다.
     * 같은 Spring 컨텍스트를 공유하므로 그 경매들의 실시간 이벤트가 같은 채널로 흘러들어와
     * 이 테스트의 호출 횟수 단언을 깨뜨린다. 충돌할 수 없는 범위를 쓴다.
     */
    private static final long ISOLATED_AUCTION_ID_BASE = 900_000L;

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
        reset(simpMessagingTemplate);
    }

    @Test
    @DisplayName("Redis 채널 발행 시 로컬 SimpMessagingTemplate으로 즉시 전달")
    void publish_to_redis_is_bridged_to_local_broker() throws Exception {
        Long auctionId = ISOLATED_AUCTION_ID_BASE + 1;
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
        Assertions.assertThat(captor.getValue().eventType())
                .isEqualTo("BID_PLACED");
    }

    @Test
    @DisplayName("연속 ACTIVE 이벤트는 모두 즉시 송출")
    void rapid_active_events_are_all_sent() throws Exception {
        Long auctionId = ISOLATED_AUCTION_ID_BASE + 2;
        for (int i = 1; i <= 3; i++) {
            AuctionBidEvent event = AuctionBidEvent.ofNewBid(
                    BigDecimal.valueOf(i * 100), "user" + i, LocalDateTime.now());
            stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + auctionId,
                    objectMapper.writeValueAsString(event));
        }

        ArgumentCaptor<AuctionBidEvent> captor = ArgumentCaptor.forClass(AuctionBidEvent.class);
        verify(simpMessagingTemplate, timeout(2000).times(3))
                .convertAndSend(eq(TOPIC_PREFIX + auctionId), captor.capture());
        Assertions.assertThat(captor.getAllValues())
                .extracting(AuctionBidEvent::currentPrice)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(200),
                        BigDecimal.valueOf(300)));
    }

    @Test
    @DisplayName("ENDED 이벤트도 Redis 수신 시 즉시 전송")
    void ended_event_is_sent_immediately() throws Exception {
        Long auctionId = ISOLATED_AUCTION_ID_BASE + 3;

        AuctionBidEvent ended = AuctionBidEvent.ofEnded("winner", BigDecimal.valueOf(500));
        stringRedisTemplate.convertAndSend(CHANNEL_PREFIX + auctionId,
                objectMapper.writeValueAsString(ended));

        verify(simpMessagingTemplate, timeout(2000).times(1))
                .convertAndSend(eq(TOPIC_PREFIX + auctionId),
                        argThat((Object payload) -> payload instanceof AuctionBidEvent
                                && ((AuctionBidEvent) payload).auctionStatus() == AuctionStatus.ENDED));
    }
}
