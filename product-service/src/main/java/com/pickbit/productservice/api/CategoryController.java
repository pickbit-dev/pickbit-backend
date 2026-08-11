package com.pickbit.productservice.api;

import com.pickbit.productservice.api.dto.request.CategoryCreateRequest;
import com.pickbit.productservice.api.dto.request.CategoryUpdateRequest;
import com.pickbit.productservice.api.dto.response.CategoryResponse;
import com.pickbit.library.auth.AuthContextHolder;
import com.pickbit.productservice.application.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 카테고리 관리 API 컨트롤러.
 * 카테고리 등록, 조회, 수정, 활성화/비활성화 기능을 제공합니다.
 */
@Tag(name = "Category", description = "카테고리 관리 API")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private static final String ADMIN_ROLE = "ADMIN";

    private final CategoryService categoryService;

    /**
     * 새 카테고리를 등록합니다. 관리자만 호출할 수 있습니다.
     *
     * @param request 카테고리 등록 요청
     * @return 등록된 카테고리 정보 (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        AuthContextHolder.requireRole(ADMIN_ROLE);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.createCategory(request));
    }

    /**
     * 활성화된 카테고리 목록을 조회합니다.
     *
     * @return 활성 카테고리 목록
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getActiveCategories() {
        return ResponseEntity.ok(categoryService.getActiveCategories());
    }

    /**
     * 전체 카테고리 목록을 조회합니다 (비활성 포함).
     *
     * @return 전체 카테고리 목록
     */
    @GetMapping("/all")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    /**
     * 카테고리 상세 정보를 조회합니다.
     *
     * @param id 카테고리 ID
     * @return 카테고리 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> getCategory(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategory(id));
    }

    /**
     * 카테고리 정보를 수정합니다. 관리자만 호출할 수 있습니다.
     *
     * @param id      수정할 카테고리 ID
     * @param request 수정할 카테고리 정보
     * @return 수정된 카테고리 정보
     */
    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request
    ) {
        AuthContextHolder.requireRole(ADMIN_ROLE);
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    /**
     * 카테고리를 활성화/비활성화합니다. 관리자만 호출할 수 있습니다.
     *
     * @param id     카테고리 ID
     * @param active 활성화 여부
     * @return 변경된 카테고리 정보
     */
    @PatchMapping("/{id}/active")
    public ResponseEntity<CategoryResponse> setActive(
            @PathVariable Long id,
            @RequestParam boolean active
    ) {
        AuthContextHolder.requireRole(ADMIN_ROLE);
        return ResponseEntity.ok(categoryService.setActive(id, active));
    }
}
