package com.pickbit.productservice.domain.category.service;

import com.pickbit.productservice.domain.category.dto.request.CreateCategoryRequest;
import com.pickbit.productservice.domain.category.dto.request.UpdateCategoryRequest;
import com.pickbit.productservice.domain.category.dto.response.CategoryResponse;
import com.pickbit.productservice.domain.category.entity.Category;
import com.pickbit.productservice.domain.category.mapper.CategoryMapper;
import com.pickbit.productservice.domain.category.repository.CategoryRepository;
import com.pickbit.productservice.exception.CategoryNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        Category parentCategory = resolveParentCategory(request.parentCategoryId(), null);
        Category category = Category.builder()
                .name(request.name())
                .description(request.description())
                .active(request.active())
                .sortOrder(request.sortOrder())
                .parentCategory(parentCategory)
                .build();
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    public CategoryResponse get(Long categoryId) {
        return categoryMapper.toResponse(getCategoryDetail(categoryId));
    }

    @Transactional
    public CategoryResponse update(Long categoryId, UpdateCategoryRequest request) {
        Category category = getCategoryDetail(categoryId);

        Category parentCategory = resolveParentCategory(request.parentCategoryId(), categoryId);
        category.update(
                request.name(),
                request.description(),
                request.active(),
                request.sortOrder(),
                parentCategory
        );
        return categoryMapper.toResponse(category);
    }

    @Transactional
    public CategoryResponse updateActive(Long categoryId, boolean active) {
        Category category = getCategoryDetail(categoryId);
        category.updateActive(active);
        return categoryMapper.toResponse(category);
    }

    private Category getCategoryDetail(Long categoryId) {
        return categoryRepository.findDetailById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private Category resolveParentCategory(Long parentCategoryId, Long currentCategoryId) {
        if (parentCategoryId == null) {
            return null;
        }
        if (currentCategoryId != null && currentCategoryId.equals(parentCategoryId)) {
            throw new IllegalArgumentException("카테고리는 자기 자신을 부모로 가질 수 없습니다.");
        }
        return categoryRepository.findById(parentCategoryId)
                .orElseThrow(() -> new CategoryNotFoundException(parentCategoryId));
    }
}
