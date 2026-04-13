package com.pickbit.productservice.api;

import com.pickbit.library.dto.PageResponse;
import com.pickbit.library.dto.PageableRequest;
import com.pickbit.productservice.api.dto.request.ProductCreateRequest;
import com.pickbit.productservice.api.dto.request.ProductUpdateRequest;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.api.dto.response.ProductSummaryResponse;
import com.pickbit.productservice.application.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
     * 상품 목록을 조회합니다.
     *
     * @param categoryId 카테고리별 조회를 위한 카테고리 ID
     * @param pageableRequest 페이지 및 정렬 요청 정보
     * @return 상품 요약 정보 목록
     */
    @GetMapping
    public ResponseEntity<PageResponse<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) Long categoryId,
            @ModelAttribute PageableRequest pageableRequest
    ) {
        return ResponseEntity.ok(productService.getProducts(categoryId, pageableRequest.toPageable(20)));
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
     * 기존 상품 정보를 수정합니다.
     *
     * @param nickname 요청한 판매자 닉네임
     * @param id 수정할 상품 ID
     * @param request 수정할 상품 정보
     * @return 수정된 상품 상세 정보
     */
    @PutMapping("/{id}")
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
