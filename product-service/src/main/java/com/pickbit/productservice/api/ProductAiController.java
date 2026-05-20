package com.pickbit.productservice.api;

import com.pickbit.productservice.api.dto.request.ProductListingSuggestionRequest;
import com.pickbit.productservice.api.dto.response.ProductListingSuggestionResponse;
import com.pickbit.productservice.application.ProductAiService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 기반 상품 등록 보조 API 컨트롤러.
 */
@Tag(name = "Product AI", description = "상품 등록 AI 추천 API")
@RestController
@RequestMapping("/api/products/ai")
@RequiredArgsConstructor
public class ProductAiController {

    private final ProductAiService productAiService;

    /**
     * 상품 제목과 간단 메모를 기반으로 카테고리와 상품 설명을 추천합니다.
     *
     * @param request 상품 등록 추천 요청
     * @return 추천 카테고리와 상품 설명
     */
    @PostMapping("/listing-suggestion")
    public ResponseEntity<ProductListingSuggestionResponse> suggestListing(
            @Valid @RequestBody ProductListingSuggestionRequest request
    ) {
        return ResponseEntity.ok(productAiService.suggestListing(request));
    }
}
