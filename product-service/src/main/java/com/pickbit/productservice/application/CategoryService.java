package com.pickbit.productservice.application;

import com.pickbit.productservice.api.dto.request.CategoryCreateRequest;
import com.pickbit.productservice.api.dto.request.CategoryUpdateRequest;
import com.pickbit.productservice.api.dto.response.CategoryResponse;
import com.pickbit.productservice.application.mapper.CategoryMapper;
import com.pickbit.productservice.domain.Category;
import com.pickbit.productservice.exception.CategoryNotFoundException;
import com.pickbit.productservice.exception.DuplicateCategoryException;
import com.pickbit.productservice.infrastructure.persistence.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"));

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new DuplicateCategoryException(request.name());
        }
        Category category = Category.builder()
                .name(request.name())
                .description(request.description())
                .active(true)
                .sortOrder(request.sortOrder())
                .build();
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = findCategory(id);
        if (!category.getName().equals(request.name()) && categoryRepository.existsByName(request.name())) {
            throw new DuplicateCategoryException(request.name());
        }
        category.update(request.name(), request.description(), category.isActive(), request.sortOrder());
        return categoryMapper.toResponse(category);
    }

    @Transactional
    public CategoryResponse setActive(Long id, boolean active) {
        Category category = findCategory(id);
        category.updateActive(active);
        return categoryMapper.toResponse(category);
    }

    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findAllByActiveTrue(DEFAULT_SORT).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll(DEFAULT_SORT).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    public CategoryResponse getCategory(Long id) {
        return categoryMapper.toResponse(findCategory(id));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }
}
