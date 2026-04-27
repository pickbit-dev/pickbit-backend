package com.pickbit.productservice.application;

import com.pickbit.productservice.api.dto.request.ProductCreateRequest;
import com.pickbit.productservice.api.dto.request.ProductImageRequest;
import com.pickbit.productservice.api.dto.request.ProductSearchCondition;
import com.pickbit.productservice.api.dto.request.ProductUpdateRequest;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.config.TestContainerConfig;
import com.pickbit.productservice.domain.Category;
import com.pickbit.productservice.domain.product.entity.enums.ImageType;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.domain.product.entity.enums.ProductSort;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import com.pickbit.productservice.exception.CategoryNotFoundException;
import com.pickbit.productservice.exception.ProductNotFoundException;
import com.pickbit.productservice.exception.UnauthorizedProductAccessException;
import com.pickbit.productservice.infrastructure.persistence.CategoryRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(TestContainerConfig.class)
@Testcontainers
@ActiveProfiles("test")
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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
            ProductDetailResponse response = productService.createProduct("seller1", defaultCreateRequest);

            assertThat(response.id()).isNotNull();
            assertThat(response.name()).isEqualTo("테스트 상품");
            assertThat(response.description()).isEqualTo("테스트 상품 설명입니다.");
            assertThat(response.startingPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
            assertThat(response.productStatus()).isEqualTo(ProductStatus.ACTIVE);
            assertThat(response.productCondition()).isEqualTo(ProductCondition.NEW);
            assertThat(response.sellerNickname()).isEqualTo("seller1");
            assertThat(response.images()).hasSize(2);
            assertThat(response.viewCount()).isZero();
        }

        @Test
        @DisplayName("이미지가 함께 저장된다")
        void createProduct_imagesArePersisted() {
            ProductDetailResponse response = productService.createProduct("seller1", defaultCreateRequest);

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
            ProductDetailResponse response = productService.createProduct("seller1", defaultCreateRequest);

            assertThat(productRepository.existsById(response.id())).isTrue();
        }

        @Test
        @DisplayName("categoryId가 주어지면 응답에 categoryName이 포함된다")
        void createProduct_withCategory() {
            Long categoryId = persistCategory("전자기기");

            ProductDetailResponse response = productService.createProduct(
                    "seller1", createRequest("스피커", BigDecimal.valueOf(5000), categoryId));

            assertThat(response.categoryId()).isEqualTo(categoryId);
            assertThat(response.categoryName()).isEqualTo("전자기기");
        }

        @Test
        @DisplayName("존재하지 않는 categoryId면 CategoryNotFoundException이 발생한다")
        void createProduct_invalidCategory() {
            assertThatThrownBy(() ->
                    productService.createProduct("seller1", createRequest("p", BigDecimal.TEN, 999999L)))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("상품 단건 조회")
    class GetProduct {

        @Test
        @DisplayName("존재하는 상품 ID로 조회하면 상세 응답을 반환한다")
        void getProduct_success() {
            ProductDetailResponse created = productService.createProduct("seller1", defaultCreateRequest);

            ProductDetailResponse response = productService.getProduct(created.id());

            assertThat(response.id()).isEqualTo(created.id());
            assertThat(response.name()).isEqualTo("테스트 상품");
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 ProductNotFoundException이 발생한다")
        void getProduct_notFound() {
            assertThatThrownBy(() -> productService.getProduct(999999L))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("DELETED 상태의 상품은 조회할 수 없다")
        void getProduct_deletedProductThrowsException() {
            ProductDetailResponse created = productService.createProduct("seller1", defaultCreateRequest);
            productService.deleteProduct("seller1", created.id());

            assertThatThrownBy(() -> productService.getProduct(created.id()))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("단건 조회 시 viewCount가 1씩 증가한다")
        void getProduct_increasesViewCount() {
            ProductDetailResponse created = productService.createProduct("seller1", defaultCreateRequest);

            productService.getProduct(created.id());
            productService.getProduct(created.id());
            ProductDetailResponse third = productService.getProduct(created.id());

            assertThat(third.viewCount()).isEqualTo(3L);
        }
    }


    @Nested
    @DisplayName("상품 수정")
    class UpdateProduct {

        @Test
        @DisplayName("소유자가 수정하면 변경 사항이 반영된다")
        void updateProduct_success() {
            ProductDetailResponse created = productService.createProduct("seller1", defaultCreateRequest);

            ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                    "수정된 상품명",
                    "수정된 설명입니다.",
                    BigDecimal.valueOf(20000),
                    ProductStatus.ACTIVE,
                    ProductCondition.GOOD,
                    null,
                    List.of(new ProductImageRequest("https://example.com/new.jpg", ImageType.THUMBNAIL, 0))
            );

            ProductDetailResponse response = productService.updateProduct("seller1", created.id(), updateRequest);

            assertThat(response.name()).isEqualTo("수정된 상품명");
            assertThat(response.description()).isEqualTo("수정된 설명입니다.");
            assertThat(response.startingPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000));
            assertThat(response.productCondition()).isEqualTo(ProductCondition.GOOD);
        }

        @Test
        @DisplayName("수정 시 이미지가 교체된다")
        void updateProduct_imagesAreReplaced() {
            ProductDetailResponse created = productService.createProduct("seller1", defaultCreateRequest);

            ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                    "수정 상품",
                    "설명",
                    BigDecimal.valueOf(5000),
                    ProductStatus.ACTIVE,
                    ProductCondition.NEW,
                    null,
                    List.of(new ProductImageRequest("https://example.com/replaced.jpg", ImageType.THUMBNAIL, 0))
            );

            ProductDetailResponse response = productService.updateProduct("seller1", created.id(), updateRequest);

            assertThat(response.images()).hasSize(1);
            assertThat(response.images().getFirst().imageUrl()).isEqualTo("https://example.com/replaced.jpg");
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 수정하면 UnauthorizedProductAccessException이 발생한다")
        void updateProduct_unauthorizedUser() {
            ProductDetailResponse created = productService.createProduct("seller1", defaultCreateRequest);

            ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                    "수정 시도",
                    "설명",
                    BigDecimal.valueOf(5000),
                    ProductStatus.ACTIVE,
                    ProductCondition.NEW,
                    null,
                    List.of(new ProductImageRequest("https://example.com/img.jpg", ImageType.THUMBNAIL, 0))
            );

            assertThatThrownBy(() -> productService.updateProduct("other_user", created.id(), updateRequest))
                    .isInstanceOf(UnauthorizedProductAccessException.class);
        }

        @Test
        @DisplayName("존재하지 않는 상품 수정 시 ProductNotFoundException이 발생한다")
        void updateProduct_notFound() {
            ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                    "수정 시도",
                    "설명",
                    BigDecimal.valueOf(5000),
                    ProductStatus.ACTIVE,
                    ProductCondition.NEW,
                    null,
                    List.of(new ProductImageRequest("https://example.com/img.jpg", ImageType.THUMBNAIL, 0))
            );

            assertThatThrownBy(() -> productService.updateProduct("seller1", 999999L, updateRequest))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("상품 삭제")
    class DeleteProduct {

        @Test
        @DisplayName("소유자가 삭제하면 이후 조회 시 ProductNotFoundException이 발생한다")
        void deleteProduct_success() {
            ProductDetailResponse created = productService.createProduct("seller1", defaultCreateRequest);

            productService.deleteProduct("seller1", created.id());

            assertThatThrownBy(() -> productService.getProduct(created.id()))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("소유자가 아닌 사용자가 삭제하면 UnauthorizedProductAccessException이 발생한다")
        void deleteProduct_unauthorizedUser() {
            ProductDetailResponse created = productService.createProduct("seller1", defaultCreateRequest);

            assertThatThrownBy(() -> productService.deleteProduct("other_user", created.id()))
                    .isInstanceOf(UnauthorizedProductAccessException.class);
        }

        @Test
        @DisplayName("존재하지 않는 상품 삭제 시 ProductNotFoundException이 발생한다")
        void deleteProduct_notFound() {
            assertThatThrownBy(() -> productService.deleteProduct("seller1", 999999L))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("상품 상태 변경 (내부 API)")
    class UpdateProductStatus {

        @Test
        @DisplayName("상품 상태를 INACTIVE로 변경할 수 있다")
        void updateProductStatus_toInactive() {
            ProductDetailResponse created = productService.createProduct("seller1", defaultCreateRequest);

            productService.updateProductStatus(created.id(), ProductStatus.INACTIVE);

            assertThat(productService.getProduct(created.id()).productStatus()).isEqualTo(ProductStatus.INACTIVE);
        }

        @Test
        @DisplayName("상품 상태를 AUCTION_COMPLETED로 변경할 수 있다")
        void updateProductStatus_toAuctionCompleted() {
            ProductDetailResponse created = productService.createProduct("seller1", defaultCreateRequest);

            productService.updateProductStatus(created.id(), ProductStatus.AUCTION_COMPLETED);

            assertThat(productService.getProduct(created.id()).productStatus()).isEqualTo(ProductStatus.AUCTION_COMPLETED);
        }

        @Test
        @DisplayName("존재하지 않는 상품 상태 변경 시 ProductNotFoundException이 발생한다")
        void updateProductStatus_notFound() {
            assertThatThrownBy(() -> productService.updateProductStatus(999999L, ProductStatus.AUCTION_COMPLETED))
                    .isInstanceOf(ProductNotFoundException.class);
        }
    }
}
