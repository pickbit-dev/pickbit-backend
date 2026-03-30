package com.pickbit.productservice.domain.product.service;

import com.pickbit.productservice.domain.category.entity.Category;
import com.pickbit.productservice.domain.category.repository.CategoryRepository;
import com.pickbit.productservice.domain.product.dto.request.CreateProductRequest;
import com.pickbit.productservice.domain.product.dto.request.ProductImageRequest;
import com.pickbit.productservice.domain.product.dto.request.UpdateProductRequest;
import com.pickbit.productservice.domain.product.dto.response.ProductDetailResponse;
import com.pickbit.productservice.domain.product.entity.Product;
import com.pickbit.productservice.domain.product.entity.ProductImage;
import com.pickbit.productservice.domain.product.entity.enums.ImageType;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import com.pickbit.productservice.domain.product.mapper.ProductMapper;
import com.pickbit.productservice.domain.product.repository.ProductRepository;
import com.pickbit.productservice.exception.CategoryNotFoundException;
import com.pickbit.productservice.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Transactional
    public ProductDetailResponse create(CreateProductRequest request) {
        Category category = getCategoryDetail(request.categoryId());
        List<ProductImage> images = toImages(request.images());
        validateImages(images);

        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .status(request.status())
                .condition(request.condition())
                .listingType(request.listingType())
                .category(category)
                .build();
        product.replaceImages(images);

        Product savedProduct = productRepository.save(product);
        return productMapper.toDetailResponse(getProductDetail(savedProduct.getId()));
    }

    public ProductDetailResponse get(Long productId) {
        return productMapper.toDetailResponse(getProductDetail(productId));
    }

    @Transactional
    public ProductDetailResponse update(Long productId, UpdateProductRequest request) {
        Product product = getProductDetail(productId);
        Category category = getCategoryDetail(request.categoryId());
        List<ProductImage> images = toImages(request.images());
        validateImages(images);

        product.update(
                request.name(),
                request.description(),
                request.price(),
                request.status(),
                request.condition(),
                request.listingType(),
                category
        );
        product.replaceImages(images);

        return productMapper.toDetailResponse(product);
    }

    @Transactional
    public ProductDetailResponse updateStatus(Long productId, ProductStatus status) {
        Product product = getProductDetail(productId);
        product.updateStatus(status);
        return productMapper.toDetailResponse(product);
    }

    private Product getProductDetail(Long productId) {
        return productRepository.findDetailById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private Category getCategoryDetail(Long categoryId) {
        return categoryRepository.findDetailById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private List<ProductImage> toImages(List<ProductImageRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .<ProductImage>map(request -> ProductImage.builder()
                        .imageUrl(request.imageUrl())
                        .imageType(request.imageType())
                        .sortOrder(request.sortOrder())
                        .build())
                .toList();
    }

    private void validateImages(List<ProductImage> images) {
        if (images.isEmpty()) {
            return;
        }

        Set<Integer> sortOrders = new HashSet<>();
        boolean hasThumbnail = false;
        for (ProductImage image : images) {
            if (!sortOrders.add(image.getSortOrder())) {
                throw new IllegalArgumentException("이미지 정렬 순서는 중복될 수 없습니다.");
            }
            if (image.getImageType() == ImageType.THUMBNAIL) {
                hasThumbnail = true;
            }
        }

        if (!hasThumbnail) {
            throw new IllegalArgumentException("대표 이미지를 하나 이상 등록해야 합니다.");
        }
    }
}
