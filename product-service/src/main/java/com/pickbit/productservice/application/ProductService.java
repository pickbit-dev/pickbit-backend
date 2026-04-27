package com.pickbit.productservice.application;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.productservice.api.dto.request.ProductCreateRequest;
import com.pickbit.productservice.api.dto.request.ProductSearchCondition;
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
import com.pickbit.productservice.infrastructure.persistence.ProductQueryRepository;
import com.pickbit.productservice.infrastructure.persistence.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductQueryRepository productQueryRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductDetailResponse createProduct(String sellerNickname, ProductCreateRequest request) {
        Category category = resolveCategory(request.categoryId());

        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .startingPrice(request.startingPrice())
                .productStatus(ProductStatus.ACTIVE)
                .productCondition(request.productCondition())
                .sellerNickname(sellerNickname)
                .category(category)
                .build();

        request.images().forEach(img -> {
            ProductImage image = ProductImage.builder()
                    .imageUrl(img.imageUrl())
                    .imageType(img.imageType())
                    .sortOrder(img.sortOrder())
                    .build();
            product.addImage(image);
        });

        return productMapper.toDetailResponse(productRepository.save(product));
    }

    public PageResponse<ProductSummaryResponse> searchProducts(ProductSearchCondition condition, Pageable pageable) {
        Page<ProductSummaryResponse> page = productQueryRepository.search(condition, pageable)
                .map(productMapper::toSummaryResponse);
        return PageResponse.from(page);
    }

    @Transactional
    public ProductDetailResponse getProduct(Long id) {
        Product product = findActiveProduct(id);
        product.increaseViewCount();
        return productMapper.toDetailResponse(product);
    }

    @Transactional
    public ProductDetailResponse updateProduct(String nickname, Long id, ProductUpdateRequest request) {

        Product product = findActiveProduct(id);

        validateOwner(product, nickname);

        Category category = resolveCategory(request.categoryId());

        product.update(
                request.name(),
                request.description(),
                request.startingPrice(),
                request.productStatus(),
                request.productCondition(),
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

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
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
