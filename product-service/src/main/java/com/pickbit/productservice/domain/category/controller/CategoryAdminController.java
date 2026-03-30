package com.pickbit.productservice.domain.category.controller;

import com.pickbit.productservice.domain.category.dto.request.CreateCategoryRequest;
import com.pickbit.productservice.domain.category.dto.request.UpdateCategoryActiveRequest;
import com.pickbit.productservice.domain.category.dto.request.UpdateCategoryRequest;
import com.pickbit.productservice.domain.category.dto.response.CategoryResponse;
import com.pickbit.productservice.domain.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/product/admin/categories")
public class CategoryAdminController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @GetMapping("/{categoryId}")
    public CategoryResponse get(@PathVariable Long categoryId) {
        return categoryService.get(categoryId);
    }

    @PatchMapping("/{categoryId}")
    public CategoryResponse update(@PathVariable Long categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.update(categoryId, request);
    }

    @PatchMapping("/{categoryId}/active")
    public CategoryResponse updateActive(@PathVariable Long categoryId, @Valid @RequestBody UpdateCategoryActiveRequest request) {
        return categoryService.updateActive(categoryId, request.active());
    }
}
