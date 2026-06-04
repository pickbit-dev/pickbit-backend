package com.pickbit.productservice.application;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.productservice.api.dto.request.ProductSearchCondition;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.api.dto.response.ProductSummaryResponse;
import com.pickbit.productservice.application.mapper.ProductMapper;
import com.pickbit.productservice.domain.Product;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import com.pickbit.productservice.exception.ProductNotFoundException;
import com.pickbit.productservice.infrastructure.persistence.ProductQueryRepository;
import com.pickbit.productservice.infrastructure.persistence.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    public PageResponse<ProductSummaryResponse> searchProducts(ProductSearchCondition condition, Pageable pageable) {
        Page<ProductSummaryResponse> page = productQueryRepository.searchSummary(condition, pageable);
        return PageResponse.from(page);
    }

    public PageResponse<ProductSummaryResponse> getSellingProducts(Long sellerUserId, ProductStatus status, Pageable pageable) {
        Page<ProductSummaryResponse> page = productQueryRepository.searchSellingProducts(sellerUserId, status, pageable);
        return PageResponse.from(page);
    }

    @Transactional
    public ProductDetailResponse getProduct(Long id) {
        Product product = findActiveProduct(id);
        product.increaseViewCount();
        return productMapper.toDetailResponse(product);
    }

    public ProductDetailResponse getInternalProduct(Long id) {
        return productMapper.toDetailResponse(findActiveProduct(id));
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
