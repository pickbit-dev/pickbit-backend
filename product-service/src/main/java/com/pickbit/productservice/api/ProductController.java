package com.pickbit.productservice.api;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.library.dto.PageableRequest;
import com.pickbit.productservice.api.dto.request.ProductCreateRequest;
import com.pickbit.productservice.api.dto.request.ProductSearchCondition;
import com.pickbit.productservice.api.dto.request.ProductUpdateRequest;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.api.dto.response.ProductSummaryResponse;
import com.pickbit.productservice.application.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 외부 클라이언트용 상품 API 컨트롤러.
 * 상품 등록, 조회, 수정, 삭제와 같은 상품 관리 기능을 제공합니다.
 */
@Tag(name = "Product", description = "상품 관리 API")
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private static final String NICKNAME_HEADER = "nickname";
    private final ProductService productService;

    /**
     * 상품 목록을 검색 조건에 따라 페이징 조회합니다.
     *
     * @param condition       검색 조건 (키워드, 카테고리, 가격 범위, 판매자)
     * @param pageableRequest 페이징 및 정렬 조건
     * @return 상품 요약 목록 (페이징)
     */
    @GetMapping
    public ResponseEntity<PageResponse<ProductSummaryResponse>> searchProducts(
            @ModelAttribute ProductSearchCondition condition,
            @ModelAttribute PageableRequest pageableRequest
    ) {
        return ResponseEntity.ok(productService.searchProducts(
                condition, pageableRequest.toPageable(20)));
    }

    /**
     * 신규 상품을 등록합니다.
     * 이미지는 file-service를 통해 사전 업로드 후 URL을 전달합니다.
     *
     * @param nickname 요청한 판매자 닉네임
     * @param request 등록할 상품 정보 (images 포함)
     * @return 등록된 상품 상세 정보 (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<ProductDetailResponse> createProduct(
            @RequestHeader(NICKNAME_HEADER) String nickname,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(nickname, request));
    }


    /**
     * 상품 상세 정보를 조회합니다.
     *
     * @param id 조회할 상품 ID
     * @return 상품 상세 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    /**
     * 기존 상품 정보를 수정합니다
     *
     * @param nickname 요청한 판매자 닉네임
     * @param id 수정할 상품 ID
     * @param request 수정할 상품 정보
     * @return 수정된 상품 상세 정보
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> updateProduct(
            @RequestHeader(NICKNAME_HEADER) String nickname,
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ResponseEntity.ok(productService.updateProduct(nickname, id, request));
    }

    /**
     * 상품을 삭제합니다.
     *
     * @param nickname 요청한 판매자 닉네임
     * @param id 삭제할 상품 ID
     * @return 응답 본문 없음 (HTTP 204)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @RequestHeader(NICKNAME_HEADER) String nickname,
            @PathVariable Long id
    ) {
        productService.deleteProduct(nickname, id);
        return ResponseEntity.noContent().build();
    }
}
