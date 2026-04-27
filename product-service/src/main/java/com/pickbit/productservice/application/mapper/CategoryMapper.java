package com.pickbit.productservice.application.mapper;

import com.pickbit.productservice.api.dto.response.CategoryResponse;
import com.pickbit.productservice.domain.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);
}
