package com.pickbit.productservice.application;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.productservice.api.dto.request.ProductCreateRequest;
import com.pickbit.productservice.api.dto.request.ProductUpdateRequest;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.api.dto.response.ProductSummaryResponse;
import com.pickbit.productservice.application.mapper.ProductMapper;
import com.pickbit.productservice.domain.Category;
import com.pickbit.productservice.domain.Product;
import com.pickbit.productservice.domain.ProductImage;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import com.pickbit.productservice.exception.CategoryNotFoundException;
import com.pickbit.productservice.exception.ProductNotFoundException;
import com.pickbit.productservice.exception.UnauthorizedProductAccessException;
import com.pickbit.productservice.infrastructure.persistence.CategoryRepository;
import com.pickbit.productservice.infrastructure.persistence.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductDetailResponse createProduct(String sellerNickname, ProductCreateRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));

        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .productStatus(ProductStatus.ACTIVE)
                .productCondition(request.productCondition())
                .listingType(request.listingType())
                .sellerNickname(sellerNickname)
                .category(category)
                .build();

        request.images().forEach(imageRequest -> {
            ProductImage image = ProductImage.builder()
                    .imageUrl(imageRequest.imageUrl())
                    .imageType(imageRequest.imageType())
                    .sortOrder(imageRequest.sortOrder())
                    .build();
            product.addImage(image);
        });

        return productMapper.toDetailResponse(productRepository.save(product));
    }

    public PageResponse<ProductSummaryResponse> getProducts(Long categoryId, Pageable pageable) {
        var products = categoryId != null
                ? productRepository.findByProductStatusNotAndCategoryId(ProductStatus.DELETED, categoryId, pageable)
                : productRepository.findByProductStatusNot(ProductStatus.DELETED, pageable);
        return PageResponse.from(products.map(productMapper::toSummaryResponse));
    }

    public ProductDetailResponse getProduct(Long id) {
        return productMapper.toDetailResponse(findActiveProduct(id));
    }

    @Transactional
    public ProductDetailResponse updateProduct(String nickname, Long id, ProductUpdateRequest request) {
        Product product = findActiveProduct(id);
        validateOwner(product, nickname);

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException(request.categoryId()));

        product.update(
                request.name(),
                request.description(),
                request.price(),
                request.productStatus(),
                request.productCondition(),
                request.listingType(),
                category
        );

        List<ProductImage> images = request.images().stream()
                .map(imageRequest -> (ProductImage) ProductImage.builder()
                        .imageUrl(imageRequest.imageUrl())
                        .imageType(imageRequest.imageType())
                        .sortOrder(imageRequest.sortOrder())
                        .build())
                .toList();
        product.replaceImages(images);

        return productMapper.toDetailResponse(product);
    }

    @Transactional
    public void deleteProduct(String nickname, Long id) {
        Product product = findActiveProduct(id);
        validateOwner(product, nickname);
        product.updateStatus(ProductStatus.DELETED);
    }

    @Transactional
    public void updateProductStatus(Long id, ProductStatus status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.updateStatus(status);
    }

    private Product findActiveProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        if (product.getProductStatus() == ProductStatus.DELETED) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }

    private void validateOwner(Product product, String nickname) {
        if (!product.getSellerNickname().equals(nickname)) {
            throw new UnauthorizedProductAccessException();
        }
    }
}
