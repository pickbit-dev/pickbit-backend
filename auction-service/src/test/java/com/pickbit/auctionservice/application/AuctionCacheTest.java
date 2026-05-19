package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.request.BidCreateRequest;
import com.pickbit.auctionservice.api.dto.response.AuctionDetailResponse;
import com.pickbit.auctionservice.config.CacheConfig;
import com.pickbit.auctionservice.config.TestContainerConfig;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.auctionservice.infrastructure.client.ProductServiceClient;
import com.pickbit.auctionservice.infrastructure.client.UserServiceClient;
import com.pickbit.auctionservice.infrastructure.client.dto.UserResponse;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@Import(TestContainerConfig.class)
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuctionCacheTest {

    @Autowired
    private AuctionQueryService auctionQueryService;

    @Autowired
    private BidCommandService bidCommandService;

    @Autowired
    private AuctionCommandService auctionCommandService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    private Long auctionId;

    @BeforeEach
    void setUp() {
        given(userServiceClient.getByNickname(anyString()))
                .willAnswer(invocation -> {
                    String nickname = invocation.getArgument(0);
                    return new UserResponse((long) nickname.hashCode(), nickname);
                });
        clearRedis();
        auctionId = transactionTemplate.execute(status ->
                auctionRepository.save(Auction.builder()
                        .productId(1L)
                        .productName("캐시 테스트 상품")
                        .sellerNickname("seller-cache")
                        .startingPrice(BigDecimal.valueOf(10_000))
                        .currentPrice(BigDecimal.valueOf(10_000))
                        .buyNowPrice(BigDecimal.valueOf(1_000_000))
                        .minimumBidIncrement(BigDecimal.valueOf(1_000))
                        .auctionStatus(AuctionStatus.ACTIVE)
                        .startTime(LocalDateTime.now().minusHours(1))
                        .endTime(LocalDateTime.now().plusDays(1))
                        .build())
                        .getId());
    }

    @AfterEach
    void cleanup() {
        transactionTemplate.execute(status -> {
            bidRepository.findByAuctionId(auctionId).forEach(bidRepository::delete);
            auctionRepository.findById(auctionId).ifPresent(auctionRepository::delete);
            return null;
        });
        clearRedis();
    }

    private void clearRedis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }
    }

    @Test
    @Order(1)
    @DisplayName("getAuction 2회 호출 시 두 번째는 캐시에서 반환되어 DB 변경이 반영되지 않는다")
    void cache_hit_returns_cached_value() {
        AuctionDetailResponse first = auctionQueryService.getAuction(auctionId);
        assertThat(first.currentPrice()).isEqualByComparingTo(BigDecimal.valueOf(10_000));

        // 캐시 우회로 DB 직접 변경 — evict 이벤트가 발행되지 않음
        transactionTemplate.execute(status -> {
            Auction a = auctionRepository.findById(auctionId).orElseThrow();
            a.placeBid(BigDecimal.valueOf(50_000));
            auctionRepository.saveAndFlush(a);
            return null;
        });

        AuctionDetailResponse second = auctionQueryService.getAuction(auctionId);
        assertThat(second.currentPrice())
                .as("캐시 hit 으로 변경 전 값이 반환되어야 함")
                .isEqualByComparingTo(BigDecimal.valueOf(10_000));

        // 캐시에 키 존재 확인
        Cache cache = cacheManager.getCache(CacheConfig.AUCTION_CACHE);
        assertThat(cache).isNotNull();
        assertCacheKeyPresent(auctionId);
    }

    @Test
    @Order(2)
    @DisplayName("입찰 처리 후 AFTER_COMMIT evict 로 캐시가 비워지고 다음 조회는 새 값을 반환한다")
    void bid_evicts_cache() {
        AuctionDetailResponse warmed = auctionQueryService.getAuction(auctionId); // warm
        assertThat(warmed.currentPrice()).isEqualByComparingTo(BigDecimal.valueOf(10_000));
        Cache cache = cacheManager.getCache(CacheConfig.AUCTION_CACHE);
        assertThat(cache).isNotNull();
        assertCacheKeyPresent(auctionId);

        bidCommandService.placeBid("bidder1", auctionId, new BidCreateRequest(BigDecimal.valueOf(15_000)));

        assertCacheKeyAbsent(auctionId, "AFTER_COMMIT 후 evict 되어 캐시 비어 있어야 함");

        AuctionDetailResponse refreshed = auctionQueryService.getAuction(auctionId);
        assertThat(refreshed.currentPrice()).isEqualByComparingTo(BigDecimal.valueOf(15_000));
    }

    @Test
    @Order(3)
    @DisplayName("취소 처리 후 AFTER_COMMIT evict 로 캐시가 비워지고 status 가 갱신된다")
    void cancel_evicts_cache() {
        Long scheduledId = transactionTemplate.execute(status ->
                auctionRepository.save(Auction.builder()
                        .productId(2L)
                        .productName("취소 테스트 상품")
                        .sellerNickname("seller-cancel")
                        .startingPrice(BigDecimal.valueOf(10_000))
                        .currentPrice(BigDecimal.valueOf(10_000))
                        .minimumBidIncrement(BigDecimal.valueOf(1_000))
                        .auctionStatus(AuctionStatus.SCHEDULED)
                        .startTime(LocalDateTime.now().plusHours(1))
                        .endTime(LocalDateTime.now().plusDays(1))
                        .build())
                        .getId());

        try {
            AuctionDetailResponse warmed = auctionQueryService.getAuction(scheduledId); // warm
            assertThat(warmed.auctionStatus()).isEqualTo(AuctionStatus.SCHEDULED);
            Cache cache = cacheManager.getCache(CacheConfig.AUCTION_CACHE);
            assertThat(cache).isNotNull();
            assertCacheKeyPresent(scheduledId);

            auctionCommandService.cancelAuction("seller-cancel", scheduledId);

            assertCacheKeyAbsent(scheduledId, "cancel AFTER_COMMIT evict");

            AuctionDetailResponse refreshed = auctionQueryService.getAuction(scheduledId);
            assertThat(refreshed.auctionStatus()).isEqualTo(AuctionStatus.CANCELLED);
        } finally {
            transactionTemplate.execute(status -> {
                auctionRepository.findById(scheduledId).ifPresent(auctionRepository::delete);
                return null;
            });
        }
    }

    @Test
    @Order(4)
    @DisplayName("TTL 이 지나면 Redis 가 키를 만료시킨다")
    void ttl_expires() throws InterruptedException {
        auctionQueryService.getAuction(auctionId); // warm

        Cache cache = cacheManager.getCache(CacheConfig.AUCTION_CACHE);
        assertThat(cache).isNotNull();
        assertCacheKeyPresent(auctionId);

        // test 프로파일 TTL = 1s → 2s 후엔 반드시 만료
        Thread.sleep(2_000);

        assertCacheKeyAbsent(auctionId, "TTL 경과 후 Redis 키 만료");
    }

    private void assertCacheKeyPresent(Long id) {
        awaitCacheKey(id, true, "캐시 키가 존재해야 함");
    }

    private void assertCacheKeyAbsent(Long id, String description) {
        awaitCacheKey(id, false, description);
    }

    private void awaitCacheKey(Long id, boolean expected, String description) {
        long deadline = System.currentTimeMillis() + 2_000;
        boolean actual;
        do {
            actual = hasCacheKey(id);
            if (actual == expected) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(description, e);
            }
        } while (System.currentTimeMillis() < deadline);

        assertThat(actual)
                .as(description + ": expected=" + cacheKey(id) + ", keys=" + cacheKeys())
                .isEqualTo(expected);
    }

    private boolean hasCacheKey(Long id) {
        return cacheKeys().contains(cacheKey(id));
    }

    private String cacheKey(Long id) {
        return "auction:cache:" + CacheConfig.AUCTION_CACHE + "::" + id;
    }

    private Set<String> cacheKeys() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Set<byte[]> keys = connection.keyCommands().keys("auction:cache:*".getBytes(StandardCharsets.UTF_8));
            return keys.stream()
                    .map(key -> new String(key, StandardCharsets.UTF_8))
                    .collect(Collectors.toSet());
        }
    }
}
