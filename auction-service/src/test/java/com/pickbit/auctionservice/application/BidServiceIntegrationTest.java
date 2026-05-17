package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.request.BidCreateRequest;
import com.pickbit.auctionservice.api.dto.response.BidResponse;
import com.pickbit.auctionservice.config.TestContainerConfig;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.auctionservice.domain.enums.BidStatus;
import com.pickbit.auctionservice.exception.AuctionNotFoundException;
import com.pickbit.auctionservice.exception.InvalidAuctionStatusException;
import com.pickbit.auctionservice.exception.InvalidBidAmountException;
import com.pickbit.auctionservice.exception.UnauthorizedAuctionAccessException;
import com.pickbit.auctionservice.infrastructure.client.ProductServiceClient;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import com.pickbit.auctionservice.infrastructure.persistence.OutBoxEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestContainerConfig.class)
@Testcontainers
@ActiveProfiles("test")
class BidServiceIntegrationTest {

    @Autowired
    private BidCommandService bidCommandService;

    @Autowired
    private BidQueryService bidQueryService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private OutBoxEventRepository outBoxEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    private Auction activeAuction;

    @BeforeEach
    void setUp() {
        activeAuction = auctionRepository.save(Auction.builder()
                .productId(1L)
                .productName("테스트 상품")
                .productThumbnailUrl(null)
                .sellerNickname("seller1")
                .startingPrice(BigDecimal.valueOf(10_000))
                .currentPrice(BigDecimal.valueOf(10_000))
                .buyNowPrice(BigDecimal.valueOf(100_000))
                .minimumBidIncrement(BigDecimal.valueOf(1_000))
                .auctionStatus(AuctionStatus.ACTIVE)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusDays(1))
                .build());
    }

    @Nested
    @DisplayName("입찰 처리")
    class PlaceBid {

        @Test
        @DisplayName("정상 입찰 시 ACTIVE 상태로 저장되고 경매 현재가가 갱신된다")
        void placeBid_success() {
            BidResponse response = bidCommandService.placeBid(
                    "bidder1", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(15_000)));

            assertThat(response.bidderNickname()).isEqualTo("bidder1");
            assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(15_000));
            assertThat(response.bidStatus()).isEqualTo(BidStatus.ACTIVE);

            Auction reloaded = auctionRepository.findById(activeAuction.getId()).orElseThrow();
            assertThat(reloaded.getCurrentPrice()).isEqualByComparingTo(BigDecimal.valueOf(15_000));
        }

        @Test
        @DisplayName("두 번째 입찰 시 직전 ACTIVE 입찰은 OUTBID로 전환된다")
        void placeBid_previousBidMarkedOutbid() {
            bidCommandService.placeBid("bidder1", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(15_000)));
            bidCommandService.placeBid("bidder2", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(20_000)));

            entityManager.flush();
            entityManager.clear();

            var bids = bidRepository.findByAuctionId(activeAuction.getId());
            assertThat(bids).hasSize(2);
            assertThat(bids).filteredOn(b -> b.getAmount().compareTo(BigDecimal.valueOf(15_000)) == 0)
                    .singleElement()
                    .extracting("bidStatus").isEqualTo(BidStatus.OUTBID);
            assertThat(bids).filteredOn(b -> b.getAmount().compareTo(BigDecimal.valueOf(20_000)) == 0)
                    .singleElement()
                    .extracting("bidStatus").isEqualTo(BidStatus.ACTIVE);
        }

        @Test
        @DisplayName("첫 입찰이 시작가 미만이면 예외가 발생한다")
        void placeBid_belowStartingPrice() {
            assertThatThrownBy(() -> bidCommandService.placeBid(
                    "bidder1", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(5_000))))
                    .isInstanceOf(InvalidBidAmountException.class);
        }

        @Test
        @DisplayName("두 번째 입찰이 현재가+최소단위 미만이면 예외가 발생한다")
        void placeBid_belowMinimumIncrement() {
            bidCommandService.placeBid("bidder1", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(15_000)));

            assertThatThrownBy(() -> bidCommandService.placeBid(
                    "bidder2", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(15_500))))
                    .isInstanceOf(InvalidBidAmountException.class);
        }

        @Test
        @DisplayName("판매자 본인은 입찰할 수 없다")
        void placeBid_sellerCannotBid() {
            assertThatThrownBy(() -> bidCommandService.placeBid(
                    "seller1", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(15_000))))
                    .isInstanceOf(UnauthorizedAuctionAccessException.class);
        }

        @Test
        @DisplayName("ACTIVE 상태가 아닌 경매에는 입찰할 수 없다")
        void placeBid_notActiveAuction() {
            Auction scheduled = auctionRepository.save(Auction.builder()
                    .productId(2L).productName("상품2").sellerNickname("seller2")
                    .startingPrice(BigDecimal.valueOf(10_000))
                    .currentPrice(BigDecimal.valueOf(10_000))
                    .minimumBidIncrement(BigDecimal.valueOf(1_000))
                    .auctionStatus(AuctionStatus.SCHEDULED)
                    .startTime(LocalDateTime.now().plusDays(1))
                    .endTime(LocalDateTime.now().plusDays(2))
                    .build());

            assertThatThrownBy(() -> bidCommandService.placeBid(
                    "bidder1", scheduled.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(15_000))))
                    .isInstanceOf(InvalidAuctionStatusException.class);
        }

        @Test
        @DisplayName("존재하지 않는 경매에 입찰하면 예외가 발생한다")
        void placeBid_auctionNotFound() {
            assertThatThrownBy(() -> bidCommandService.placeBid(
                    "bidder1", 999_999L,
                    new BidCreateRequest(BigDecimal.valueOf(15_000))))
                    .isInstanceOf(AuctionNotFoundException.class);
        }

        @Test
        @DisplayName("즉시 구매가 이상으로 입찰하면 경매가 ENDED로 종료되고 product-service 상태가 갱신된다")
        void placeBid_buyNowEndsAuction() {
            BidResponse response = bidCommandService.placeBid(
                    "bidder1", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(100_000)));

            assertThat(response.bidStatus()).isEqualTo(BidStatus.WINNING);

            Auction reloaded = auctionRepository.findById(activeAuction.getId()).orElseThrow();
            assertThat(reloaded.getAuctionStatus()).isEqualTo(AuctionStatus.ENDED);
            assertThat(reloaded.getWinnerNickname()).isEqualTo("bidder1");
            assertThat(reloaded.getFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(100_000));

            assertThat(outBoxEventRepository.findAll())
                    .filteredOn(e -> "Product".equals(e.getEntity()))
                    .filteredOn(e -> "Product:1".equals(e.getAggregateId()))
                    .filteredOn(e -> "UPDATE".equals(e.getEventType()))
                    .singleElement()
                    .extracting(e -> e.getPayload().contains("AUCTION_COMPLETED"))
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("일반 입찰은 product-service 상태 갱신을 호출하지 않는다")
        void placeBid_normalDoesNotUpdateProductStatus() {
            bidCommandService.placeBid("bidder1", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(15_000)));

            assertThat(outBoxEventRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("입찰 이력 조회")
    class BidHistory {

        @Test
        @DisplayName("경매의 입찰 이력을 금액 내림차순으로 페이징해 반환한다")
        void getBidHistory_success() {
            bidCommandService.placeBid("bidder1", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(15_000)));
            bidCommandService.placeBid("bidder2", activeAuction.getId(),
                    new BidCreateRequest(BigDecimal.valueOf(20_000)));

            Page<BidResponse> page = bidQueryService.getBidHistory(
                    activeAuction.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent())
                    .extracting("amount")
                    .containsExactly(BigDecimal.valueOf(20_000), BigDecimal.valueOf(15_000));
        }

        @Test
        @DisplayName("존재하지 않는 경매의 입찰 이력 조회 시 예외가 발생한다")
        void getBidHistory_auctionNotFound() {
            assertThatThrownBy(() -> bidQueryService.getBidHistory(
                    999_999L, PageRequest.of(0, 10)))
                    .isInstanceOf(AuctionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("동시성 - 낙관적 잠금")
    class OptimisticLockConcurrency {

        @Test
        @DisplayName("두 스레드가 stale 상태로 동일 Auction 수정 시 한 쪽은 ObjectOptimisticLockingFailureException")
        @Transactional(propagation = Propagation.NEVER)
        void detects_stale_write() throws Exception {
            Long auctionId = createCommittedAuction();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch readDone = new CountDownLatch(2);
                CountDownLatch releaseWrite = new CountDownLatch(1);

                Callable<Throwable> staleWriteTask = () -> {
                    try {
                        transactionTemplate.execute(status -> {
                            Auction a = auctionRepository.findById(auctionId).orElseThrow();
                            readDone.countDown();
                            try {
                                releaseWrite.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return null;
                            }
                            a.placeBid(BigDecimal.valueOf(20_000));
                            auctionRepository.saveAndFlush(a);
                            return null;
                        });
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                };

                Future<Throwable> f1 = executor.submit(staleWriteTask);
                Future<Throwable> f2 = executor.submit(staleWriteTask);

                assertThat(readDone.await(5, TimeUnit.SECONDS)).isTrue();
                releaseWrite.countDown();

                Throwable r1 = f1.get(10, TimeUnit.SECONDS);
                Throwable r2 = f2.get(10, TimeUnit.SECONDS);

                long failures = Stream.of(r1, r2)
                        .filter(t -> t instanceof ObjectOptimisticLockingFailureException)
                        .count();
                long successes = Stream.of(r1, r2).filter(Objects::isNull).count();

                assertThat(failures).isEqualTo(1);
                assertThat(successes).isEqualTo(1);
            } finally {
                executor.shutdown();
                cleanupCommittedAuction(auctionId);
            }
        }

        @Test
        @DisplayName("BidCommandService를 통한 동시 입찰은 Redisson 락이 직렬화하여 ObjectOptimisticLockingFailureException이 발생하지 않는다")
        @Transactional(propagation = Propagation.NEVER)
        void redisson_lock_prevents_version_conflict() throws Exception {
            Long auctionId = createCommittedAuction();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);

                Callable<Throwable> bidTask = () -> {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        bidCommandService.placeBid(
                                "bidder-" + Thread.currentThread().getId(),
                                auctionId,
                                new BidCreateRequest(BigDecimal.valueOf(15_000)));
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                };

                Future<Throwable> f1 = executor.submit(bidTask);
                Future<Throwable> f2 = executor.submit(bidTask);
                start.countDown();

                Throwable r1 = f1.get(10, TimeUnit.SECONDS);
                Throwable r2 = f2.get(10, TimeUnit.SECONDS);

                long versionConflicts = Stream.of(r1, r2)
                        .filter(t -> t instanceof ObjectOptimisticLockingFailureException)
                        .count();
                long successes = Stream.of(r1, r2).filter(Objects::isNull).count();
                long invalidAmountFailures = Stream.of(r1, r2)
                        .filter(t -> t instanceof InvalidBidAmountException)
                        .count();

                assertThat(versionConflicts)
                        .as("Redisson 락이 직렬화하므로 버전 충돌이 발생하면 안 됨")
                        .isZero();
                assertThat(successes).isEqualTo(1);
                assertThat(invalidAmountFailures).isEqualTo(1);

                Auction reloaded = transactionTemplate.execute(status ->
                        auctionRepository.findById(auctionId).orElseThrow());
                assertThat(reloaded.getCurrentPrice()).isEqualByComparingTo(BigDecimal.valueOf(15_000));
            } finally {
                executor.shutdown();
                cleanupCommittedAuction(auctionId);
            }
        }

        private Long createCommittedAuction() {
            return transactionTemplate.execute(status ->
                    auctionRepository.save(Auction.builder()
                            .productId(99L)
                            .productName("동시성 테스트 상품")
                            .sellerNickname("seller-cc")
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

        private void cleanupCommittedAuction(Long auctionId) {
            transactionTemplate.execute(status -> {
                bidRepository.findByAuctionId(auctionId).forEach(bidRepository::delete);
                auctionRepository.findById(auctionId).ifPresent(auctionRepository::delete);
                return null;
            });
        }
    }
}
