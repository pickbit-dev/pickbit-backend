package com.pickbit.productservice.application.mapper;

import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.api.dto.response.ProductImageResponse;
import com.pickbit.productservice.api.dto.response.ProductSummaryResponse;
import com.pickbit.productservice.domain.Product;
import com.pickbit.productservice.domain.ProductImage;
import com.pickbit.productservice.domain.product.entity.enums.ImageType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "updatedAt", source = "updatedDate")
    ProductDetailResponse toDetailResponse(Product product);

    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "createdAt", source = "createdDate")
    @Mapping(target = "thumbnailUrl", expression = "java(extractThumbnailUrl(product))")
    ProductSummaryResponse toSummaryResponse(Product product);

    ProductImageResponse toImageResponse(ProductImage image);

    default String extractThumbnailUrl(Product product) {
        return product.getImages().stream()
                .filter(img -> img.getImageType() == ImageType.THUMBNAIL)
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(null);
    }
}