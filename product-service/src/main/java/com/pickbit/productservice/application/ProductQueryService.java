package com.pickbit.productservice.application;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.productservice.api.dto.request.ProductSearchCondition;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.api.dto.response.ProductSummaryResponse;
import com.pickbit.productservice.application.mapper.ProductMapper;
import com.pickbit.productservice.domain.Product;
import com.pickbit.productservice.infrastructure.redis.ViewCountBuffer;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import com.pickbit.productservice.exception.ProductNotFoundException;
import com.pickbit.productservice.infrastructure.client.AuctionServiceClient;
import com.pickbit.productservice.infrastructure.persistence.ProductQueryRepository;
import com.pickbit.productservice.infrastructure.persistence.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository productRepository;
    private final ProductQueryRepository productQueryRepository;
    private final ProductMapper productMapper;
    private final AuctionServiceClient auctionServiceClient;
    private final ViewCountBuffer viewCountBuffer;
    private final ProductDetailReader productDetailReader;

    public PageResponse<ProductSummaryResponse> searchProducts(ProductSearchCondition condition, Pageable pageable) {
        Page<ProductSummaryResponse> page = productQueryRepository.searchSummary(condition, applyProductSort(condition, pageable));
        return PageResponse.from(page);
    }

    public PageResponse<ProductSummaryResponse> getSellingProducts(Long sellerUserId, ProductStatus status, Pageable pageable) {
        Page<ProductSummaryResponse> page = productQueryRepository.searchSellingProducts(sellerUserId, status, pageable);
        return PageResponse.from(page);
    }

    /**
     * 상품 상세를 조회합니다.
     *
     * <p>읽기 트래픽의 대부분을 차지하므로 캐싱합니다. 예전에는 조회 때마다 조회수 UPDATE 가
     * 실행돼서 사실상 쓰기 API였고 캐싱을 해도 DB 부하가 그대로였습니다. 지금은 조회수를
     * Redis 에 모으고 스케줄러가 반영하므로 이 경로에서 DB 쓰기가 없습니다.
     *
     * <p>조회수 증가는 <b>캐시 바깥</b>에 있어야 합니다. 캐시된 메서드 안에 두면 캐시 적중 시
     * 호출되지 않아 조회수가 적중률만큼 누락됩니다.
     */
    public ProductDetailResponse getProduct(Long id) {
        ProductDetailResponse response = productDetailReader.read(id);
        viewCountBuffer.increase(id);
        return response;
    }

    public ProductDetailResponse getInternalProduct(Long id) {
        return productMapper.toDetailResponse(findActiveProduct(id));
    }

    private java.time.LocalDateTime getAuctionStartTime(Product product) {
        if (product.getProductStatus() != ProductStatus.AUCTION_SCHEDULED) {
            return null;
        }
        return auctionServiceClient.getScheduledAuctionStartTime(product.getId());
    }

    private Pageable applyProductSort(ProductSearchCondition condition, Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), condition.sortOrDefault().toSort());
    }

    private Product findActiveProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if (product.getProductStatus() == ProductStatus.DELETED) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }
}
