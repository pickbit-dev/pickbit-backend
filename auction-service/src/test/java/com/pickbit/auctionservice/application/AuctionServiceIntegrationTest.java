package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.request.AuctionCreateRequest;
import com.pickbit.auctionservice.api.dto.response.AuctionDetailResponse;
import com.pickbit.auctionservice.api.dto.response.AuctionSummaryResponse;
import com.pickbit.auctionservice.config.TestContainerConfig;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.Bid;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.auctionservice.domain.enums.BidStatus;
import com.pickbit.auctionservice.exception.AuctionNotFoundException;
import com.pickbit.auctionservice.exception.InvalidAuctionStatusException;
import com.pickbit.auctionservice.exception.InvalidProductForAuctionException;
import com.pickbit.auctionservice.exception.UnauthorizedAuctionAccessException;
import com.pickbit.auctionservice.infrastructure.client.ProductServiceClient;
import com.pickbit.auctionservice.infrastructure.client.dto.ProductResponse;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import com.pickbit.auctionservice.infrastructure.persistence.OutBoxEventRepository;
import com.pickbit.library.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
@Import(TestContainerConfig.class)
@Testcontainers
@ActiveProfiles("test")
class AuctionServiceIntegrationTest {

    @Autowired
    private AuctionCommandService auctionCommandService;

    @Autowired
    private AuctionQueryService auctionQueryService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private OutBoxEventRepository outBoxEventRepository;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    private AuctionCreateRequest defaultRequest;
    private ProductResponse defaultProduct;

    @BeforeEach
    void setUp() {

        defaultRequest = new AuctionCreateRequest(
                1L,
                BigDecimal.valueOf(10_000),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(1_000),
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(2)
        );
        defaultProduct = new ProductResponse(
                1L,
                "테스트 상품",
                "설명",
                BigDecimal.valueOf(10_000),
                "ACTIVE",
                "NEW",
                1L,
                "seller1",
                10L,
                "전자기기",
                List.of()
        );
    }

    @Nested
    @DisplayName("경매 생성")
    class CreateAuction {

        @Test
        @DisplayName("정상 요청 시 SCHEDULED 상태로 저장되고 상세 응답을 반환한다")
        void createAuction_success() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);

            AuctionDetailResponse response = auctionCommandService.createAuction(1L, "seller1", defaultRequest);

            assertThat(response.id()).isNotNull();
            assertThat(response.productId()).isEqualTo(1L);
            assertThat(response.sellerNickname()).isEqualTo("seller1");
            assertThat(response.startingPrice()).isEqualByComparingTo(BigDecimal.valueOf(10_000));
            assertThat(response.currentPrice()).isEqualByComparingTo(BigDecimal.valueOf(10_000));
            assertThat(response.auctionStatus()).isEqualTo(AuctionStatus.SCHEDULED);
            assertThat(auctionRepository.existsById(response.id())).isTrue();
            verify(productServiceClient).reserveForAuction(1L, 1L);
            assertThat(outBoxEventRepository.findAll())
                    .filteredOn(e -> "Product".equals(e.getEntity()))
                    .isEmpty();
        }

        @Test
        @DisplayName("상품이 ACTIVE 상태가 아니면 예외가 발생한다")
        void createAuction_productNotActive() {
            ProductResponse soldOut = new ProductResponse(
                    1L, "테스트 상품", "설명", BigDecimal.valueOf(10_000),
                    "SOLD_OUT", "NEW", 1L, "seller1", 10L, "전자기기", List.of()
            );
            given(productServiceClient.getProduct(1L)).willReturn(soldOut);

            assertThatThrownBy(() -> auctionCommandService.createAuction(1L, "seller1", defaultRequest))
                    .isInstanceOf(InvalidProductForAuctionException.class);
        }

        @Test
        @DisplayName("판매자 계정 ID가 일치하지 않으면 예외가 발생한다")
        void createAuction_sellerMismatch() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);

            assertThatThrownBy(() -> auctionCommandService.createAuction(2L, "seller1", defaultRequest))
                    .isInstanceOf(UnauthorizedAuctionAccessException.class);
        }

        @Test
        @DisplayName("진행 중이거나 예정된 동일 상품의 경매가 이미 있으면 예외가 발생한다")
        void createAuction_duplicateActiveAuction() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);
            auctionCommandService.createAuction(1L, "seller1", defaultRequest);

            assertThatThrownBy(() -> auctionCommandService.createAuction(1L, "seller1", defaultRequest))
                    .isInstanceOf(InvalidAuctionStatusException.class);
        }

        @Test
        @DisplayName("종료 시각이 시작 시각보다 이르거나 같으면 예외가 발생한다")
        void createAuction_endBeforeStart() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);
            LocalDateTime now = LocalDateTime.now();
            AuctionCreateRequest invalid = new AuctionCreateRequest(
                    1L,
                    BigDecimal.valueOf(10_000),
                    BigDecimal.valueOf(100_000),
                    BigDecimal.valueOf(1_000),
                    now.plusDays(2),
                    now.plusDays(1)
            );

            assertThatThrownBy(() -> auctionCommandService.createAuction(1L, "seller1", invalid))
                    .isInstanceOf(InvalidAuctionStatusException.class);
        }
    }

    @Nested
    @DisplayName("경매 단건 조회")
    class GetAuction {

        @Test
        @DisplayName("존재하는 경매 ID로 조회하면 상세 응답을 반환한다")
        void getAuction_success() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);
            AuctionDetailResponse created = auctionCommandService.createAuction(1L, "seller1", defaultRequest);

            AuctionDetailResponse response = auctionQueryService.getAuction(created.id());

            assertThat(response.id()).isEqualTo(created.id());
            assertThat(response.productId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않는 경매 ID로 조회하면 예외가 발생한다")
        void getAuction_notFound() {
            assertThatThrownBy(() -> auctionQueryService.getAuction(999_999L))
                    .isInstanceOf(AuctionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("경매 목록 조회")
    class GetAuctions {

        @Test
        @DisplayName("status 없이 조회하면 모든 경매를 반환한다")
        void getAuctions_withoutStatusFilter() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);
            auctionCommandService.createAuction(1L, "seller1", defaultRequest);

            PageResponse<AuctionSummaryResponse> page =
                    auctionQueryService.getAuctions(null, PageRequest.of(0, 10));

            assertThat(page.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("status로 필터하면 해당 상태의 경매만 반환한다")
        void getAuctions_withStatusFilter() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);
            auctionCommandService.createAuction(1L, "seller1", defaultRequest);

            PageResponse<AuctionSummaryResponse> scheduled =
                    auctionQueryService.getAuctions(AuctionStatus.SCHEDULED, PageRequest.of(0, 10));
            PageResponse<AuctionSummaryResponse> active =
                    auctionQueryService.getAuctions(AuctionStatus.ACTIVE, PageRequest.of(0, 10));

            assertThat(scheduled.totalElements()).isEqualTo(1);
            assertThat(active.totalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("경매 취소")
    class CancelAuction {

        @Test
        @DisplayName("판매자 본인이 SCHEDULED 경매를 취소하면 CANCELLED로 변경된다")
        void cancelAuction_success() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);
            AuctionDetailResponse created = auctionCommandService.createAuction(1L, "seller1", defaultRequest);

            auctionCommandService.cancelAuction(1L, created.id());

            Auction reloaded = auctionRepository.findById(created.id()).orElseThrow();
            assertThat(reloaded.getAuctionStatus()).isEqualTo(AuctionStatus.CANCELLED);
            assertThat(outBoxEventRepository.findAll())
                    .filteredOn(e -> "Product".equals(e.getEntity()))
                    .filteredOn(e -> "Product:1".equals(e.getAggregateId()))
                    .filteredOn(e -> "UPDATE".equals(e.getEventType()))
                    .filteredOn(e -> e.getPayload().contains("AUCTION_CANCELLED"))
                    .singleElement();
        }

        @Test
        @DisplayName("판매자 본인이 아니면 예외가 발생한다")
        void cancelAuction_notOwner() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);
            AuctionDetailResponse created = auctionCommandService.createAuction(1L, "seller1", defaultRequest);

            assertThatThrownBy(() -> auctionCommandService.cancelAuction(2L, created.id()))
                    .isInstanceOf(UnauthorizedAuctionAccessException.class);
        }

        @Test
        @DisplayName("입찰 없는 ACTIVE 경매는 취소할 수 있다")
        void cancelAuction_activeWithoutBids() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);
            AuctionDetailResponse created = auctionCommandService.createAuction(1L, "seller1", defaultRequest);
            auctionRepository.findById(created.id()).orElseThrow().activate();

            auctionCommandService.cancelAuction(1L, created.id());

            Auction reloaded = auctionRepository.findById(created.id()).orElseThrow();
            assertThat(reloaded.getAuctionStatus()).isEqualTo(AuctionStatus.CANCELLED);
        }

        @Test
        @DisplayName("입찰 있는 ACTIVE 경매는 취소할 수 없다")
        void cancelAuction_activeWithBids() {
            given(productServiceClient.getProduct(1L)).willReturn(defaultProduct);
            AuctionDetailResponse created = auctionCommandService.createAuction(1L, "seller1", defaultRequest);
            Auction auction = auctionRepository.findById(created.id()).orElseThrow();
            auction.activate();
            bidRepository.save(Bid.builder()
                    .auction(auction)
                    .bidderUserId(101L)
                    .bidderNickname("bidder1")
                    .amount(BigDecimal.valueOf(15_000))
                    .bidTime(LocalDateTime.now())
                    .bidStatus(BidStatus.ACTIVE)
                    .build());

            assertThatThrownBy(() -> auctionCommandService.cancelAuction(1L, created.id()))
                    .isInstanceOf(InvalidAuctionStatusException.class);
        }
    }
}
