package com.pickbit.productservice.application;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.productservice.api.dto.request.ProductCreateRequest;
import com.pickbit.productservice.api.dto.request.ProductImageRequest;
import com.pickbit.productservice.api.dto.request.ProductSearchCondition;
import com.pickbit.productservice.api.dto.request.ProductUpdateRequest;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.api.dto.response.ProductSummaryResponse;
import com.pickbit.productservice.application.event.PaymentEventHandler;
import com.pickbit.productservice.application.event.ProductStatusEventHandler;
import com.pickbit.productservice.config.TestContainerConfig;
import com.pickbit.productservice.domain.Category;
import com.pickbit.productservice.domain.product.entity.enums.ImageType;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.domain.product.entity.enums.ProductSort;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import com.pickbit.productservice.exception.CategoryNotFoundException;
import com.pickbit.productservice.exception.ProductNotFoundException;
import com.pickbit.productservice.exception.UnauthorizedProductAccessException;
import com.pickbit.productservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.productservice.infrastructure.client.AuctionServiceClient;
import com.pickbit.productservice.infrastructure.persistence.CategoryRepository;
import com.pickbit.productservice.infrastructure.persistence.InboxRepository;
import com.pickbit.productservice.infrastructure.persistence.ProductRepository;
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
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
@Import(TestContainerConfig.class)
@Testcontainers
@ActiveProfiles("test")
class ProductServiceIntegrationTest {

    @Autowired
    private ProductCommandService productCommandService;

    @Autowired
    private ProductQueryService productQueryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductStatusEventHandler productStatusEventHandler;

    @Autowired
    private PaymentEventHandler paymentEventHandler;

    @Autowired
    private InboxRepository inboxRepository;

    @Autowired
    private com.pickbit.productservice.infrastructure.redis.ViewCountBuffer viewCountBuffer;

    @Autowired
    private ViewCountFlushScheduler viewCountFlushScheduler;

    @MockitoBean
    private AuctionServiceClient auctionServiceClient;

    private ProductCreateRequest defaultCreateRequest;

    @BeforeEach
    void setUp() {
        defaultCreateRequest = new ProductCreateRequest(
                "테스트 상품",
                "테스트 상품 설명입니다.",
                BigDecimal.valueOf(10000),
                ProductCondition.NEW,
                null,
                List.of(
                        new ProductImageRequest("https://example.com/thumb.jpg", ImageType.THUMBNAIL, 0),
                        new ProductImageRequest("https://example.com/detail.jpg", ImageType.DETAIL, 1)
                )
        );
    }

    private Long persistCategory(String name) {
        Category category = Category.builder()
                .name(name)
                .description(name + " 설명")
                .active(true)
                .sortOrder(0)
                .build();
        return categoryRepository.save(category).getId();
    }

    private ProductCreateRequest createRequest(String name, BigDecimal price, Long categoryId) {
        return new ProductCreateRequest(
                name,
                name + " 설명",
                price,
                ProductCondition.NEW,
                categoryId,
                List.of(new ProductImageRequest("https://example.com/" + name + ".jpg", ImageType.THUMBNAIL, 0))
        );
    }

    @Nested
    @DisplayName("상품 등록")
    class CreateProduct {

        @Test
        @DisplayName("정상 요청 시 상품이 저장되고 상세 응답을 반환한다")
        void createProduct_success() {
            ProductDetailResponse response = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            assertThat(response.id()).isNotNull();
            assertThat(response.name()).isEqualTo("테스트 상품");
            assertThat(response.description()).isEqualTo("테스트 상품 설명입니다.");
            assertThat(response.startingPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
            assertThat(response.productStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(response.productCondition()).isEqualTo(ProductCondition.NEW);
            assertThat(response.sellerUserId()).isEqualTo(1L);
            assertThat(response.sellerNickname()).isEqualTo("seller1");
            assertThat(response.images()).hasSize(2);
            assertThat(response.viewCount()).isZero();
        }

        @Test
        @DisplayName("이미지가 함께 저장된다")
        void createProduct_imagesArePersisted() {
            ProductDetailResponse response = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            assertThat(response.images())
                    .extracting("imageUrl")
                    .containsExactlyInAnyOrder(
                            "https://example.com/thumb.jpg",
                            "https://example.com/detail.jpg"
                    );
        }

        @Test
        @DisplayName("등록된 상품은 DB에서 조회된다")
        void createProduct_persistedToDatabase() {
            ProductDetailResponse response = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            assertThat(productRepository.existsById(response.id())).isTrue();
        }

        @Test
        @DisplayName("categoryId가 주어지면 응답에 categoryName이 포함된다")
        void createProduct_withCategory() {
            Long categoryId = persistCategory("전자기기");

            ProductDetailResponse response = productCommandService.createProduct(
                    1L, "seller1", createRequest("스피커", BigDecimal.valueOf(5000), categoryId));

            assertThat(response.categoryId()).isEqualTo(categoryId);
            assertThat(response.categoryName()).isEqualTo("전자기기");
        }

        @Test
        @DisplayName("존재하지 않는 categoryId면 CategoryNotFoundException이 발생한다")
        void createProduct_invalidCategory() {
            assertThatThrownBy(() ->
                    productCommandService.createProduct(1L, "seller1", createRequest("p", BigDecimal.TEN, 999999L)))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("상품 단건 조회")
    class GetProduct {

        @Test
        @DisplayName("존재하는 상품 ID로 조회하면 상세 응답을 반환한다")
        void getProduct_success() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            ProductDetailResponse response = productQueryService.getProduct(created.id());

            assertThat(response.id()).isEqualTo(created.id());
            assertThat(response.name()).isEqualTo("테스트 상품");
            assertThat(response.auctionStartTime()).isNull();
            verify(auctionServiceClient, never()).getScheduledAuctionStartTime(created.id());
        }

        @Test
        @DisplayName("AUCTION_SCHEDULED 상품이면 예정 경매 시작 시각을 포함한다")
        void getProduct_scheduledAuctionIncludesStartTime() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);
            productCommandService.updateProductStatus(created.id(), ProductStatus.AUCTION_SCHEDULED);
            LocalDateTime auctionStartTime = LocalDateTime.now().plusDays(1).withNano(0);
            given(auctionServiceClient.getScheduledAuctionStartTime(created.id())).willReturn(auctionStartTime);

            ProductDetailResponse response = productQueryService.getProduct(created.id());

            assertThat(response.productStatus()).isEqualTo(ProductStatus.AUCTION_SCHEDULED);
            assertThat(response.auctionStartTime()).isEqualTo(auctionStartTime);
            verify(auctionServiceClient).getScheduledAuctionStartTime(created.id());
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 ProductNotFoundException이 발생한다")
        void getProduct_notFound() {
            assertThatThrownBy(() -> productQueryService.getProduct(999999L))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("DELETED 상태의 상품은 조회할 수 없다")
        void getProduct_deletedProductThrowsException() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);
            productCommandService.deleteProduct(1L, created.id());

            assertThatThrownBy(() -> productQueryService.getProduct(created.id()))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("조회수는 Redis에 모였다가 flush 시점에 DB로 반영된다")
        void viewCount_isBufferedThenFlushed() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            productQueryService.getProduct(created.id());
            productQueryService.getProduct(created.id());
            productQueryService.getProduct(created.id());

            // 조회 경로에서는 DB에 쓰지 않는다. 예전에는 조회마다 UPDATE 가 나가서
            // 캐싱을 해도 DB 부하가 그대로였고 인기 상품은 같은 행에 UPDATE 가 몰렸다.
            assertThat(viewCountBuffer.pending(created.id())).isEqualTo(3L);
            assertThat(productRepository.findById(created.id()).orElseThrow().getViewCount()).isZero();

            viewCountFlushScheduler.flush();

            assertThat(productRepository.findById(created.id()).orElseThrow().getViewCount()).isEqualTo(3L);
            // 반영한 뒤에는 버퍼를 비워 다음 주기에 중복 반영되지 않는다.
            assertThat(viewCountBuffer.pending(created.id())).isZero();
        }
    }


    @Nested
    @DisplayName("상품 수정")
    class UpdateProduct {

        @Test
        @DisplayName("소유자가 수정하면 변경 사항이 반영된다")
        void updateProduct_success() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                    "수정된 상품명",
                    "수정된 설명입니다.",
                    BigDecimal.valueOf(20000),
                    ProductCondition.GOOD,
                    null,
                    List.of(new ProductImageRequest("https://example.com/new.jpg", ImageType.THUMBNAIL, 0))
            );

            ProductDetailResponse response = productCommandService.updateProduct(1L, created.id(), updateRequest);

            assertThat(response.name()).isEqualTo("수정된 상품명");
            assertThat(response.description()).isEqualTo("수정된 설명입니다.");
            assertThat(response.startingPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000));
            assertThat(response.productCondition()).isEqualTo(ProductCondition.GOOD);
        }

        @Test
        @DisplayName("수정 시 이미지가 교체된다")
        void updateProduct_imagesAreReplaced() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                    "수정 상품",
                    "설명",
                    BigDecimal.valueOf(5000),
                    ProductCondition.NEW,
                    null,
                    List.of(new ProductImageRequest("https://example.com/replaced.jpg", ImageType.THUMBNAIL, 0))
            );

            ProductDetailResponse response = productCommandService.updateProduct(1L, created.id(), updateRequest);

            assertThat(response.images()).hasSize(1);
            assertThat(response.images().getFirst().imageUrl()).isEqualTo("https://example.com/replaced.jpg");
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 수정하면 UnauthorizedProductAccessException이 발생한다")
        void updateProduct_unauthorizedUser() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                    "수정 시도",
                    "설명",
                    BigDecimal.valueOf(5000),
                    ProductCondition.NEW,
                    null,
                    List.of(new ProductImageRequest("https://example.com/img.jpg", ImageType.THUMBNAIL, 0))
            );

            assertThatThrownBy(() -> productCommandService.updateProduct(2L, created.id(), updateRequest))
                    .isInstanceOf(UnauthorizedProductAccessException.class);
        }

        @Test
        @DisplayName("존재하지 않는 상품 수정 시 ProductNotFoundException이 발생한다")
        void updateProduct_notFound() {
            ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                    "수정 시도",
                    "설명",
                    BigDecimal.valueOf(5000),
                    ProductCondition.NEW,
                    null,
                    List.of(new ProductImageRequest("https://example.com/img.jpg", ImageType.THUMBNAIL, 0))
            );

            assertThatThrownBy(() -> productCommandService.updateProduct(1L, 999999L, updateRequest))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("상품 삭제")
    class DeleteProduct {

        @Test
        @DisplayName("소유자가 삭제하면 이후 조회 시 ProductNotFoundException이 발생한다")
        void deleteProduct_success() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            productCommandService.deleteProduct(1L, created.id());

            assertThatThrownBy(() -> productQueryService.getProduct(created.id()))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 삭제하면 UnauthorizedProductAccessException이 발생한다")
        void deleteProduct_unauthorizedUser() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            assertThatThrownBy(() -> productCommandService.deleteProduct(2L, created.id()))
                    .isInstanceOf(UnauthorizedProductAccessException.class);
        }

        @Test
        @DisplayName("존재하지 않는 상품 삭제 시 ProductNotFoundException이 발생한다")
        void deleteProduct_notFound() {
            assertThatThrownBy(() -> productCommandService.deleteProduct(1L, 999999L))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("상품 상태 변경 (내부 API)")
    class UpdateProductStatus {

        @Test
        @DisplayName("상품 상태를 INACTIVE로 변경할 수 있다")
        void updateProductStatus_toInactive() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            productCommandService.updateProductStatus(created.id(), ProductStatus.INACTIVE);

            assertThat(productQueryService.getProduct(created.id()).productStatus()).isEqualTo(ProductStatus.INACTIVE);
        }

        @Test
        @DisplayName("상품 상태를 AUCTION_COMPLETED로 변경할 수 있다")
        void updateProductStatus_toAuctionCompleted() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);

            productCommandService.updateProductStatus(created.id(), ProductStatus.AUCTION_SCHEDULED);
            productCommandService.updateProductStatus(created.id(), ProductStatus.IN_AUCTION);
            productCommandService.updateProductStatus(created.id(), ProductStatus.AUCTION_COMPLETED);

            assertThat(productQueryService.getProduct(created.id()).productStatus()).isEqualTo(ProductStatus.AUCTION_COMPLETED);
        }

        @Test
        @DisplayName("존재하지 않는 상품 상태 변경 시 ProductNotFoundException이 발생한다")
        void updateProductStatus_notFound() {
            assertThatThrownBy(() -> productCommandService.updateProductStatus(999999L, ProductStatus.AUCTION_COMPLETED))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("상품 상태 이벤트로 AUCTION_SCHEDULED 상태로 변경하고 중복 이벤트는 기록하지 않는다")
        void productStatusEventHandler_scheduleAuctionAndSkipDuplicate() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);
            String eventId = "auction-service-test-event-1";
            String payload = "{\"eventId\":\"%s\",\"productId\":%d,\"status\":\"AUCTION_SCHEDULED\",\"reason\":\"AUCTION_CREATED\",\"auctionId\":10}"
                    .formatted(eventId, created.id());

            productStatusEventHandler.handleUpdate(eventId, "Product:" + created.id(), payload, 1001L);

            assertThat(productQueryService.getProduct(created.id()).productStatus()).isEqualTo(ProductStatus.AUCTION_SCHEDULED);
            assertThat(inboxRepository.existsBySuccessEventId(eventId)).isTrue();
            assertThatThrownBy(() -> productStatusEventHandler.handleUpdate(eventId, "Product:" + created.id(), payload, 1002L))
                    .isInstanceOf(KafkaDuplicateEventException.class);
        }

        @Test
        @DisplayName("상품 상태 이벤트로 AUCTION_SCHEDULED에서 IN_AUCTION으로 변경할 수 있다")
        void productStatusEventHandler_startAuction() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);
            productCommandService.updateProductStatus(created.id(), ProductStatus.AUCTION_SCHEDULED);
            String eventId = "auction-service-test-event-2";
            String payload = "{\"eventId\":\"%s\",\"productId\":%d,\"status\":\"IN_AUCTION\",\"reason\":\"AUCTION_STARTED\",\"auctionId\":10}"
                    .formatted(eventId, created.id());

            productStatusEventHandler.handleUpdate(eventId, "Product:" + created.id(), payload, 1003L);

            assertThat(productQueryService.getProduct(created.id()).productStatus()).isEqualTo(ProductStatus.IN_AUCTION);
        }
    }

    @Nested
    @DisplayName("결제 이벤트 상품 상태 변경")
    class PaymentProductStatus {

        @Test
        @DisplayName("ESCROWED 이벤트 수신 시 AUCTION_COMPLETED 상품을 TRADE_IN_PROGRESS로 변경한다")
        void paymentEscrowed_marksTradeInProgress() {
            ProductDetailResponse created = createAuctionCompletedProduct();
            String eventId = "payment-escrowed-event-1";
            String payload = "{\"eventId\":\"%s\",\"paymentId\":1,\"auctionId\":10,\"productId\":%d,\"buyerUserId\":100,\"sellerUserId\":1,\"amount\":10000,\"paidAt\":\"2026-06-17T10:00:00\",\"confirmDeadlineAt\":\"2026-06-24T10:00:00\"}"
                    .formatted(eventId, created.id());

            paymentEventHandler.handleEscrowed(eventId, "Payment:1", payload, 1004L);

            assertThat(productQueryService.getProduct(created.id()).productStatus()).isEqualTo(ProductStatus.TRADE_IN_PROGRESS);
            assertThat(inboxRepository.existsBySuccessEventId(eventId)).isTrue();
        }

        @Test
        @DisplayName("SETTLED 이벤트 수신 시 TRADE_IN_PROGRESS 상품을 SOLD로 변경한다")
        void paymentSettled_marksSold() {
            ProductDetailResponse created = createAuctionCompletedProduct();
            productCommandService.markTradeInProgress(created.id());
            String eventId = "payment-settled-event-1";
            String payload = "{\"eventId\":\"%s\",\"paymentId\":1,\"auctionId\":10,\"productId\":%d,\"buyerUserId\":100,\"sellerUserId\":1,\"grossAmount\":10000,\"netSellerAmount\":10000,\"releasedAt\":\"2026-06-17T10:00:00\"}"
                    .formatted(eventId, created.id());

            paymentEventHandler.handleSettled(eventId, "Payment:1", payload, 1005L);

            assertThat(productQueryService.getProduct(created.id()).productStatus()).isEqualTo(ProductStatus.SOLD);
            assertThat(inboxRepository.existsBySuccessEventId(eventId)).isTrue();
        }

        @Test
        @DisplayName("REFUNDED 이벤트 수신 시 TRADE_IN_PROGRESS 상품을 INACTIVE로 변경한다")
        void paymentRefunded_marksInactive() {
            ProductDetailResponse created = createAuctionCompletedProduct();
            productCommandService.markTradeInProgress(created.id());
            String eventId = "payment-refunded-event-1";
            String payload = "{\"eventId\":\"%s\",\"paymentId\":1,\"auctionId\":10,\"productId\":%d,\"buyerUserId\":100,\"sellerUserId\":1,\"amount\":10000,\"reason\":\"환불\",\"refundedAt\":\"2026-06-17T10:00:00\"}"
                    .formatted(eventId, created.id());

            paymentEventHandler.handleRefunded(eventId, "Payment:1", payload, 1006L);

            assertThat(productQueryService.getProduct(created.id()).productStatus()).isEqualTo(ProductStatus.INACTIVE);
            assertThat(inboxRepository.existsBySuccessEventId(eventId)).isTrue();
        }

        private ProductDetailResponse createAuctionCompletedProduct() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", defaultCreateRequest);
            productCommandService.updateProductStatus(created.id(), ProductStatus.AUCTION_SCHEDULED);
            productCommandService.updateProductStatus(created.id(), ProductStatus.IN_AUCTION);
            productCommandService.updateProductStatus(created.id(), ProductStatus.AUCTION_COMPLETED);
            return created;
        }
    }

    @Nested
    @DisplayName("상품 검색")
    class SearchProducts {

        private Long categoryId;

        @BeforeEach
        void setUpProducts() {
            categoryId = persistCategory("전자기기");
            Long otherId = persistCategory("의류");

            productCommandService.createProduct(1L, "seller1", createRequest("아이폰 15", BigDecimal.valueOf(50000), categoryId));
            productCommandService.createProduct(1L, "seller1", createRequest("갤럭시 S24", BigDecimal.valueOf(30000), categoryId));
            productCommandService.createProduct(2L, "seller2", createRequest("맥북 프로", BigDecimal.valueOf(100000), categoryId));
            productCommandService.createProduct(2L, "seller2", createRequest("나이키 운동화", BigDecimal.valueOf(20000), otherId));
        }

        @Test
        @DisplayName("조건 없이 검색하면 DELETED가 아닌 전체 상품이 조회된다")
        void searchProducts_noCondition() {
            ProductSearchCondition condition = new ProductSearchCondition(null, null, null, null, null, null);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            assertThat(result.totalElements()).isEqualTo(4);
            assertThat(result.content()).hasSize(4);
        }

        @Test
        @DisplayName("상품 상태 목록으로 필터링할 수 있다")
        void searchProducts_byProductStatuses() {
            ProductDetailResponse scheduled = productCommandService.createProduct(
                    1L, "seller1", createRequest("예약 상품", BigDecimal.valueOf(40000), categoryId));
            ProductDetailResponse inAuction = productCommandService.createProduct(
                    1L, "seller1", createRequest("진행 상품", BigDecimal.valueOf(45000), categoryId));
            productCommandService.updateProductStatus(scheduled.id(), ProductStatus.AUCTION_SCHEDULED);
            productCommandService.updateProductStatus(inAuction.id(), ProductStatus.AUCTION_SCHEDULED);
            productCommandService.updateProductStatus(inAuction.id(), ProductStatus.IN_AUCTION);
            ProductSearchCondition condition = new ProductSearchCondition(
                    null, null, null, null, null, ProductSort.LATEST,
                    List.of(ProductStatus.ACTIVE, ProductStatus.AUCTION_SCHEDULED));

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            assertThat(result.content()).extracting(ProductSummaryResponse::productStatus)
                    .containsOnly(ProductStatus.ACTIVE, ProductStatus.AUCTION_SCHEDULED);
            assertThat(result.content()).extracting(ProductSummaryResponse::name)
                    .contains("예약 상품")
                    .doesNotContain("진행 상품");
        }

        @Test
        @DisplayName("키워드로 상품명을 검색할 수 있다")
        void searchProducts_byKeyword() {
            ProductSearchCondition condition = new ProductSearchCondition("아이폰", null, null, null, null, null);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().getFirst().name()).isEqualTo("아이폰 15");
        }

        @Test
        @DisplayName("키워드로 상품 설명을 검색할 수 있다")
        void searchProducts_byDescriptionKeyword() {
            ProductSearchCondition condition = new ProductSearchCondition("맥북 프로", null, null, null, null, null);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content().getFirst().name()).isEqualTo("맥북 프로");
        }

        @Test
        @DisplayName("카테고리 ID로 필터링할 수 있다")
        void searchProducts_byCategoryId() {
            ProductSearchCondition condition = new ProductSearchCondition(null, categoryId, null, null, null, null);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            assertThat(result.totalElements()).isEqualTo(3);
            assertThat(result.content()).allMatch(p -> p.categoryName().equals("전자기기"));
        }

        @Test
        @DisplayName("판매자 닉네임으로 필터링할 수 있다")
        void searchProducts_bySellerNickname() {
            ProductSearchCondition condition = new ProductSearchCondition(null, null, "seller1", null, null, null);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            assertThat(result.totalElements()).isEqualTo(2);
            assertThat(result.content()).allMatch(p -> p.sellerNickname().equals("seller1"));
        }

        @Test
        @DisplayName("가격 범위로 필터링할 수 있다")
        void searchProducts_byPriceRange() {
            ProductSearchCondition condition = new ProductSearchCondition(
                    null, null, null, BigDecimal.valueOf(25000), BigDecimal.valueOf(60000), null);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            assertThat(result.totalElements()).isEqualTo(2);
            assertThat(result.content()).allMatch(p ->
                    p.startingPrice().compareTo(BigDecimal.valueOf(25000)) >= 0
                            && p.startingPrice().compareTo(BigDecimal.valueOf(60000)) <= 0);
        }

        @Test
        @DisplayName("여러 조건을 조합하여 검색할 수 있다")
        void searchProducts_combinedConditions() {
            ProductSearchCondition condition = new ProductSearchCondition(
                    null, categoryId, "seller1", null, null, null);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            assertThat(result.totalElements()).isEqualTo(2);
            assertThat(result.content()).allMatch(p ->
                    p.sellerNickname().equals("seller1") && p.categoryName().equals("전자기기"));
        }

        @Test
        @DisplayName("DELETED 상태의 상품은 검색 결과에서 제외된다")
        void searchProducts_excludesDeleted() {
            ProductDetailResponse created = productCommandService.createProduct(1L, "seller1", createRequest("삭제 상품", BigDecimal.valueOf(1000), null));
            productCommandService.deleteProduct(1L, created.id());

            ProductSearchCondition condition = new ProductSearchCondition("삭제 상품", null, null, null, null, null);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            assertThat(result.totalElements()).isZero();
        }

        @Test
        @DisplayName("페이징이 정상 동작한다")
        void searchProducts_pagination() {
            ProductSearchCondition condition = new ProductSearchCondition(null, null, null, null, null, null);

            PageResponse<ProductSummaryResponse> firstPage = productQueryService.searchProducts(condition, PageRequest.of(0, 2));
            PageResponse<ProductSummaryResponse> secondPage = productQueryService.searchProducts(condition, PageRequest.of(1, 2));

            assertThat(firstPage.content()).hasSize(2);
            assertThat(firstPage.totalElements()).isEqualTo(4);
            assertThat(firstPage.totalPages()).isEqualTo(2);
            assertThat(firstPage.first()).isTrue();
            assertThat(firstPage.last()).isFalse();

            assertThat(secondPage.content()).hasSize(2);
            assertThat(secondPage.first()).isFalse();
            assertThat(secondPage.last()).isTrue();
        }

        @Test
        @DisplayName("가격 오름차순으로 정렬할 수 있다")
        void searchProducts_sortByPriceAsc() {
            ProductSearchCondition condition = new ProductSearchCondition(null, null, null, null, null, ProductSort.PRICE_ASC);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(
                    condition, PageRequest.of(0, 20));

            List<BigDecimal> prices = result.content().stream().map(ProductSummaryResponse::startingPrice).toList();
            assertThat(prices).isSortedAccordingTo(Comparator.naturalOrder());
        }

        @Test
        @DisplayName("최신순으로 정렬할 수 있다")
        void searchProducts_sortByLatest() {
            ProductSearchCondition condition = new ProductSearchCondition(null, null, null, null, null, ProductSort.LATEST);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            List<Long> ids = result.content().stream().map(ProductSummaryResponse::id).toList();
            assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder());
        }

        @Test
        @DisplayName("검색 결과가 없으면 빈 페이지를 반환한다")
        void searchProducts_noResults() {
            ProductSearchCondition condition = new ProductSearchCondition("존재하지않는상품", null, null, null, null, null);

            PageResponse<ProductSummaryResponse> result = productQueryService.searchProducts(condition, PageRequest.of(0, 20));

            assertThat(result.totalElements()).isZero();
            assertThat(result.content()).isEmpty();
        }
    }
}
