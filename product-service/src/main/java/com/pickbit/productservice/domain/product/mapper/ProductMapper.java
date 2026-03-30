package com.pickbit.productservice.domain.product.mapper;

import com.pickbit.productservice.domain.product.dto.response.ProductDetailResponse;
import com.pickbit.productservice.domain.product.dto.response.ProductImageResponse;
import com.pickbit.productservice.domain.product.entity.Product;
import com.pickbit.productservice.domain.product.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ProductDetailResponse toDetailResponse(Product product);

    ProductImageResponse toResponse(ProductImage image);
}
