package com.pickbit.productservice.api;

import com.pickbit.productservice.api.dto.request.ProductStatusUpdateRequest;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;
import com.pickbit.productservice.application.ProductCommandService;
import com.pickbit.productservice.application.ProductQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 서비스 간 통신 전용 상품 API 컨트롤러.
 *
 * <p>외부 클라이언트가 아닌 다른 내부 서비스(예: auction-service)에서만 호출되어야 합니다.
 * 현재는 별도의 인증 없이 동작하며, 추후 서비스 메시 또는 내부 API 키로 보호할 예정입니다.
 */
@Tag(name = "Internal Product", description = "내부 서비스 간 상품 상태 관리 API")
@RestController
@RequestMapping("/api/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductCommandService productCommandService;
    private final ProductQueryService productQueryService;

    /**
     * 내부 서비스에서 사용할 상품 상세 정보를 조회합니다.
     *
     * @param id 조회할 상품 ID
     * @return 내부 검증에 필요한 상품 상세 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> getInternalProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productQueryService.getInternalProduct(id));
    }

    /**
     * 상품의 상태를 변경합니다.
     *
     * <p>경매 종료 시 auction-service가 호출하여 상품 상태를 {@code AUCTION_COMPLETED} 또는
     * {@code ACTIVE}(유찰 시 재등록)로 업데이트합니다.
     *
     * @param id      상태를 변경할 상품 ID
     * @param request 변경할 상태 정보
     * @return 응답 본문 없음 (HTTP 204)
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateProductStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusUpdateRequest request
    ) {
        productCommandService.updateProductStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }
}
