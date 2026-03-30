package com.pickbit.productservice.domain.category.mapper;

import com.pickbit.productservice.domain.category.dto.response.CategoryResponse;
import com.pickbit.productservice.domain.category.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoryMapper {

    @Mapping(target = "parentCategoryId", source = "parentCategory.id")
    CategoryResponse toResponse(Category category);
}
