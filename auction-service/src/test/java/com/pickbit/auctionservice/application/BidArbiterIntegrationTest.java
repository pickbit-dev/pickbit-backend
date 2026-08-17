package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.request.BidCreateRequest;
import com.pickbit.auctionservice.api.dto.response.BidResponse;
import com.pickbit.auctionservice.config.TestContainerConfig;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.auctionservice.exception.InvalidBidAmountException;
import com.pickbit.auctionservice.exception.UnauthorizedAuctionAccessException;
import com.pickbit.auctionservice.infrastructure.client.ProductServiceClient;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionEventRepository;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import com.pickbit.auctionservice.infrastructure.redis.AuctionRedisKeys;
import com.pickbit.auctionservice.infrastructure.redis.AuctionState;
import com.pickbit.auctionservice.infrastructure.redis.AuctionStateStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Redis 중재 경로를 켠 상태로 검증합니다.
 *
 * <p><b>이 클래스가 존재하는 이유</b> — {@code application-test.yml} 은 중재를
 * {@code false} 로 꺼둡니다. 기존 통합 테스트가 "입찰 직후 DB에 반영되어 있다"는 동기 계약을
 * 검증하기 때문인데, 그 결과 <b>프로덕션 기본값인 중재 경로가 테스트로 전혀 커버되지 않았습니다.</b>
 * 실제로 아래 두 가지 결함이 그 사각지대에 숨어 있었습니다.
 *
 * <ul>
 *   <li>중재 경로는 트랜잭션 없이 이벤트를 발행하는데 리스너가
 *       {@code @TransactionalEventListener(AFTER_COMMIT)} 라 실시간 이벤트가 아예 발행되지 않았다</li>
 *   <li>Lua 의 {@code TOO_LOW} 분기만 반환 원소가 5개라 최저가 미달 입찰이 400 이 아니라 500 이었다</li>
 * </ul>
 *
 * <p>클래스 단위 {@code @Transactional} 을 붙이지 않습니다. 중재 경로는 영속화 워커가 별도
 * 스레드/트랜잭션에서 DB에 기록하므로, 테스트 트랜잭션 안에 갇히면 워커가 아무것도 볼 수 없습니다.
 */
@SpringBootTest
@Import(TestContainerConfig.class)
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = "auction.bid.arbiter.enabled=true")
class BidArbiterIntegrationTest {

    @Autowired
    private BidCommandService bidCommandService;

    @Autowired
    private BidBatchPersister bidBatchPersister;

    @Autowired
    private AuctionStateStore stateStore;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private AuctionEventRepository auctionEventRepository;

    @Autowired
    private StringRedisTemplate redis;

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    private Auction auction;

    @BeforeEach
    void setUp() {
        reset(simpMessagingTemplate);
        auction = auctionRepository.save(Auction.builder()
                .productId(1L)
                .productName("중재 테스트 상품")
                .productThumbnailUrl(null)
                .sellerUserId(1L)
                .sellerNickname("seller1")
                .startingPrice(BigDecimal.valueOf(10_000))
                .currentPrice(BigDecimal.valueOf(10_000))
                .buyNowPrice(BigDecimal.valueOf(100_000))
                .minimumBidIncrement(BigDecimal.valueOf(1_000))
                .auctionStatus(AuctionStatus.ACTIVE)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .build());
        // 앞선 테스트가 남긴 상태가 없어야 첫 입찰이 NOT_LOADED -> hydrate 경로를 탄다.
        redis.delete(AuctionRedisKeys.state(auction.getId()));
    }

    private BidResponse bid(long userId, String nickname, long amount) {
        return bidCommandService.placeBid(userId, nickname, auction.getId(),
                new BidCreateRequest(BigDecimal.valueOf(amount)));
    }

    @Nested
    @DisplayName("입찰 거절")
    class Rejection {

        @Test
        @DisplayName("첫 입찰이 시작가보다 낮으면 InvalidBidAmountException 이다")
        void firstBidBelowStartingPriceIsValidationError() {
            // Lua 의 TOO_LOW 분기가 5개 원소만 반환하던 시절에는 BidArbiter 의 형태 검사에 걸려
            // IllegalStateException(500) 이 났다. 가장 흔한 거절 사유가 서버 오류로 나가고 있었다.
            // 입찰이 아직 없을 때의 하한은 시작가(10,000)다.
            assertThatThrownBy(() -> bid(101L, "bidder1", 9_000))
                    .isInstanceOf(InvalidBidAmountException.class)
                    .hasMessageContaining("10000");
        }

        @Test
        @DisplayName("직전 입찰가 + 최소 단위에 못 미치면 InvalidBidAmountException 이다")
        void bidBelowRequiredIncrementIsValidationError() {
            bid(101L, "bidder1", 15_000);

            // 입찰이 있으면 하한이 현재가(15,000) + 최소 단위(1,000) 로 올라간다.
            assertThatThrownBy(() -> bid(102L, "bidder2", 15_500))
                    .isInstanceOf(InvalidBidAmountException.class)
                    .hasMessageContaining("16000");
        }

        @Test
        @DisplayName("판매자 본인은 입찰할 수 없다")
        void sellerCannotBid() {
            assertThatThrownBy(() -> bid(1L, "seller1", 15_000))
                    .isInstanceOf(UnauthorizedAuctionAccessException.class);
        }
    }

    @Nested
    @DisplayName("실시간 이벤트")
    class Realtime {

        @Test
        @DisplayName("입찰이 수락되면 WebSocket 으로 전달된다")
        void acceptedBidReachesWebSocket() {
            bid(101L, "bidder1", 15_000);

            // 발행 -> Redis pub/sub -> 구독자 -> SimpMessagingTemplate 전 구간을 탄다.
            // 리스너에 fallbackExecution 이 없던 시절에는 중재 경로에 트랜잭션이 없어
            // 이 호출이 단 한 번도 일어나지 않았다.
            verify(simpMessagingTemplate, timeout(3000).atLeast(1))
                    .convertAndSend(eq("/topic/auctions/" + auction.getId()), any(Object.class));
        }
    }

    @Nested
    @DisplayName("즉시 구매")
    class BuyNow {

        @Test
        @DisplayName("즉시 구매가 도달 시 종료되고 종료 순번이 입찰 순번과 다르다")
        void buyNowUsesSeparateSequence() {
            bid(101L, "bidder1", 100_000);

            AuctionState state = stateStore.read(auction.getId());
            assertThat(state).isNotNull();
            assertThat(state.status()).isEqualTo("ENDED");
            // 입찰 이벤트와 종료 이벤트가 같은 순번을 쓰면 클라이언트의 afterEventId 복구가
            // 둘 중 하나를 건너뛴다. 순번이 2 이상 진행됐어야 한다.
            assertThat(state.sequence()).isGreaterThanOrEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("비동기 영속화")
    class Persistence {

        @Test
        @DisplayName("입찰이 스트림을 거쳐 DB에 반영된다")
        void bidEventuallyReachesDatabase() {
            bid(101L, "bidder1", 15_000);

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(bidRepository.findByAuctionId(auction.getId()))
                            .anySatisfy(saved -> assertThat(saved.getAmount())
                                    .isEqualByComparingTo(BigDecimal.valueOf(15_000))));
        }

        @Test
        @DisplayName("같은 배치를 다시 영속화해도 행이 늘지 않는다")
        void replayingTheSameBatchIsNoOp() {
            // 워커는 DB 커밋 후에 XACK 한다. 그 사이에 죽으면 같은 배치가 재배달되므로
            // 영속화가 멱등하지 않으면 입찰이 중복 INSERT 된다.
            List<BidRecord> batch = List.of(new BidRecord(
                    auction.getId(), 101L, "bidder1", BigDecimal.valueOf(15_000),
                    1L, LocalDateTime.now(), false, 0L));

            bidBatchPersister.persist(batch);
            long bidsAfterFirst = bidRepository.findByAuctionId(auction.getId()).size();
            long eventsAfterFirst = auctionEventRepository.countByAuctionId(auction.getId());

            bidBatchPersister.persist(batch);

            assertThat(bidRepository.findByAuctionId(auction.getId())).hasSize((int) bidsAfterFirst);
            assertThat(auctionEventRepository.countByAuctionId(auction.getId())).isEqualTo(eventsAfterFirst);
        }
    }

    @Nested
    @DisplayName("상태 로드")
    class Hydration {

        @Test
        @DisplayName("이미 상태가 있으면 hydrate 가 현재가와 순번을 되감지 않는다")
        void hydrateDoesNotRewindLiveState() {
            bid(101L, "bidder1", 15_000);

            AuctionState before = stateStore.read(auction.getId());
            assertThat(before).isNotNull();

            // DB 의 경매는 아직 15,000 이 반영되기 전일 수 있다. 그 값으로 덮어쓰면
            // 이미 지나간 금액으로 다시 입찰할 수 있고 순번이 중복된다.
            Auction stale = auctionRepository.findById(auction.getId()).orElseThrow();
            boolean created = stateStore.hydrate(stale, 0L);

            assertThat(created).isFalse();
            AuctionState after = stateStore.read(auction.getId());
            assertThat(after).isNotNull();
            assertThat(after.currentPrice()).isEqualByComparingTo(before.currentPrice());
            assertThat(after.sequence()).isEqualTo(before.sequence());
        }

        @Test
        @DisplayName("상태가 없으면 순번 발급이 -1 을 돌려줘 DB 폴백으로 넘어간다")
        void missingStateReportsUnavailable() {
            redis.delete(AuctionRedisKeys.state(auction.getId()));

            assertThat(stateStore.nextSequence(auction.getId()))
                    .isEqualTo(AuctionStateStore.SEQUENCE_UNAVAILABLE);
            // 없는 키에 HINCRBY 를 보내 seq=1 짜리 해시를 새로 만들면 안 된다.
            assertThat(stateStore.read(auction.getId())).isNull();
        }
    }
}
