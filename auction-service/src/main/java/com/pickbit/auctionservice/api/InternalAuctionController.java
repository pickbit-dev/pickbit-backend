package com.pickbit.auctionservice.api;

import com.pickbit.auctionservice.api.dto.response.ScheduledAuctionResponse;
import com.pickbit.auctionservice.application.AuctionQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내부 서비스 간 통신 전용 경매 API 컨트롤러입니다.
 */
@Tag(name = "Internal Auction", description = "내부 서비스 간 경매 조회 API")
@RestController
@RequestMapping("/api/internal/auctions")
@RequiredArgsConstructor
public class InternalAuctionController {

    private final AuctionQueryService auctionQueryService;

    /**
     * 상품 ID로 예정된 경매의 시작 시각을 조회합니다.
     *
     * @param productId 상품 ID
     * @return 예정된 경매 시작 시각
     */
    @GetMapping("/products/{productId}/scheduled")
    public ResponseEntity<ScheduledAuctionResponse> getScheduledAuctionByProductId(@PathVariable Long productId) {
        return ResponseEntity.ok(auctionQueryService.getScheduledAuctionByProductId(productId));
    }
}
