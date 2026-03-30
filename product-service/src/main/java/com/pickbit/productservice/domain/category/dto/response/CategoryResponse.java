package com.pickbit.productservice.domain.category.dto.response;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        boolean active,
        Integer sortOrder,
        Long parentCategoryId,
        LocalDateTime createdDate,
        LocalDateTime updatedDate
) {
}
