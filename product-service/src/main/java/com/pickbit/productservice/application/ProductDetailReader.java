package com.pickbit.productservice.application;

import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.application.mapper.ProductMapper;
import com.pickbit.productservice.config.CacheConfig;
import com.pickbit.productservice.domain.Product;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import com.pickbit.productservice.exception.ProductNotFoundException;
import com.pickbit.productservice.infrastructure.client.AuctionServiceClient;
import com.pickbit.productservice.infrastructure.persistence.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 상품 상세 조회의 캐시 경계입니다.
 *
 * <p>조회수 증가와 분리하기 위해 별도 빈으로 둡니다. 같은 클래스 안에서 호출하면
 * 프록시를 타지 않아 {@code @Cacheable} 이 동작하지 않기 때문입니다.
 */
@Component
@RequiredArgsConstructor
public class ProductDetailReader {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final AuctionServiceClient auctionServiceClient;

    /**
     * 경매 예정 상품은 시작 시각을 auction-service 에서 가져오는데 그 값이 바뀔 수 있으므로
     * 캐시하지 않습니다.
     */
    @Cacheable(value = CacheConfig.PRODUCT_DETAIL_CACHE, key = "#id",
            unless = "#result == null || #result.auctionStartTime() != null")
    @Transactional(readOnly = true)
    public ProductDetailResponse read(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if (product.getProductStatus() == ProductStatus.DELETED) {
            throw new ProductNotFoundException(id);
        }
        return productMapper.toDetailResponse(product, auctionStartTime(product));
    }

    private LocalDateTime auctionStartTime(Product product) {
        if (product.getProductStatus() != ProductStatus.AUCTION_SCHEDULED) {
            return null;
        }
        return auctionServiceClient.getScheduledAuctionStartTime(product.getId());
    }
}
