package com.pickbit.productservice.application;

import com.pickbit.productservice.api.dto.request.ProductCreateRequest;
import com.pickbit.productservice.api.dto.request.ProductUpdateRequest;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.application.mapper.ProductMapper;
import com.pickbit.productservice.domain.Category;
import com.pickbit.productservice.domain.Product;
import com.pickbit.productservice.domain.ProductImage;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import com.pickbit.productservice.exception.CategoryNotFoundException;
import com.pickbit.productservice.exception.InvalidProductStatusException;
import com.pickbit.productservice.exception.ProductNotFoundException;
import com.pickbit.productservice.exception.UnauthorizedProductAccessException;
import com.pickbit.productservice.infrastructure.persistence.CategoryRepository;
import com.pickbit.productservice.infrastructure.persistence.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    public ProductDetailResponse createProduct(Long sellerUserId, String sellerNickname, ProductCreateRequest request) {
        Category category = resolveCategory(request.categoryId());

        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .startingPrice(request.startingPrice())
                .productStatus(ProductStatus.ACTIVE)
                .productCondition(request.productCondition())
                .sellerUserId(sellerUserId)
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

    public ProductDetailResponse updateProduct(Long sellerUserId, Long id, ProductUpdateRequest request) {
        Product product = findActiveProduct(id);

        validateOwner(product, sellerUserId);
        validateEditable(product);

        Category category = resolveCategory(request.categoryId());

        product.update(
                request.name(),
                request.description(),
                request.startingPrice(),
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

    public void deleteProduct(Long sellerUserId, Long id) {
        Product product = findActiveProduct(id);
        validateOwner(product, sellerUserId);
        try {
            product.delete();
        } catch (IllegalStateException e) {
            throw new InvalidProductStatusException(e.getMessage());
        }
    }

    public void updateProductStatus(Long id, ProductStatus status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        updateStatus(product, status);
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

    private void validateOwner(Product product, Long sellerUserId) {
        if (!sellerUserId.equals(product.getSellerUserId())) {
            throw new UnauthorizedProductAccessException();
        }
    }

    private void validateEditable(Product product) {
        if (product.getProductStatus() == ProductStatus.IN_AUCTION
                || product.getProductStatus() == ProductStatus.AUCTION_SCHEDULED
                || product.getProductStatus() == ProductStatus.AUCTION_COMPLETED) {
            throw new InvalidProductStatusException("현재 상태에서는 상품을 수정할 수 없습니다. 상태=" + product.getProductStatus());
        }
    }

    private void updateStatus(Product product, ProductStatus status) {
        try {
            switch (status) {
                case ACTIVE -> product.releaseFromAuction();
                case AUCTION_SCHEDULED -> product.scheduleAuction();
                case IN_AUCTION -> product.startAuction();
                case AUCTION_COMPLETED -> product.completeAuction();
                case INACTIVE -> product.deactivate();
                case DELETED -> product.delete();
            }
        } catch (IllegalStateException e) {
            throw new InvalidProductStatusException(e.getMessage());
        }
    }
}
