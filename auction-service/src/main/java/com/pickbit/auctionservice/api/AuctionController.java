package com.pickbit.auctionservice.api;

import com.pickbit.auctionservice.api.dto.request.AuctionCreateRequest;
import com.pickbit.auctionservice.api.dto.response.AuctionDetailResponse;
import com.pickbit.auctionservice.api.dto.response.AuctionSummaryResponse;
import com.pickbit.auctionservice.application.AuctionCommandService;
import com.pickbit.auctionservice.application.AuctionQueryService;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.library.auth.AuthContextHolder;
import com.pickbit.library.dto.PageResponse;
import com.pickbit.library.dto.PageableRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경매 관리 API 컨트롤러.
 *
 * <p>경매 생성, 목록 조회, 상세 조회, 취소, 조기 낙찰 기능을 제공합니다.
 * 판매자 식별은 게이트웨이에서 전달한 인증 컨텍스트를 통해 이루어집니다.
 */
@Tag(name = "Auction", description = "경매 관리 API")
@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionCommandService auctionCommandService;
    private final AuctionQueryService auctionQueryService;

    /**
     * 새 경매를 생성합니다.
     *
     * <p>요청자가 해당 상품의 판매자여야 하며, 상품은 {@code ACTIVE} 상태여야 합니다.
     *
     * @param request  경매 생성 요청 데이터
     * @return 생성된 경매 상세 정보 (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<AuctionDetailResponse> createAuction(
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(auctionCommandService.createAuction(
                        AuthContextHolder.getUserId(), AuthContextHolder.getNickname(), request));
    }

    /**
     * 경매 목록을 페이징 조회합니다.
     *
     * @param status          경매 상태 필터 (선택). null이면 전체 조회
     * @param pageableRequest 페이징 및 정렬 조건
     * @return 경매 요약 목록 (페이징)
     */
    @GetMapping
    public ResponseEntity<PageResponse<AuctionSummaryResponse>> getAuctions(
            @RequestParam(required = false) AuctionStatus status,
            @ModelAttribute PageableRequest pageableRequest
    ) {
        return ResponseEntity.ok(auctionQueryService.getAuctions(status, pageableRequest.toPageable(20)));
    }

    /**
     * 경매 상세 정보를 조회합니다.
     *
     * @param auctionId 경매 ID
     * @return 경매 상세 정보
     */
    @GetMapping("/{auctionId}")
    public ResponseEntity<AuctionDetailResponse> getAuction(@PathVariable Long auctionId) {
        return ResponseEntity.ok(auctionQueryService.getAuction(auctionId));
    }

    /**
     * 상품 ID로 가장 최근 경매 상세 정보를 조회합니다.
     *
     * @param productId 상품 ID
     * @return 상품에 연결된 가장 최근 경매 상세 정보
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<AuctionDetailResponse> getLatestAuctionByProductId(@PathVariable Long productId) {
        return ResponseEntity.ok(auctionQueryService.getLatestAuctionByProductId(productId));
    }

    /**
     * 경매를 취소합니다.
     *
     * <p>요청자가 해당 경매의 판매자여야 합니다.
     * 판매자는 {@code SCHEDULED} 상태의 경매를 취소할 수 있습니다.
     * {@code ACTIVE} 상태의 경매는 입찰이 없는 경우에만 취소할 수 있습니다.
     * 입찰이 있는 {@code ACTIVE} 경매와 종료된 경매는 취소할 수 없습니다.
     *
     * @param auctionId 취소할 경매 ID
     * @return 응답 본문 없음 (HTTP 204)
     */
    @DeleteMapping("/{auctionId}")
    public ResponseEntity<Void> cancelAuction(
            @PathVariable Long auctionId
    ) {
        auctionCommandService.cancelAuction(AuthContextHolder.getUserId(), auctionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 현재 최고 입찰자를 조기 낙찰자로 확정합니다.
     *
     * <p>{@code ACTIVE} 상태의 경매만 조기 낙찰할 수 있으며,
     * 요청자가 해당 경매의 판매자여야 합니다.
     * 입찰이 없는 경매는 조기 낙찰할 수 없습니다.
     *
     * @param auctionId 조기 낙찰할 경매 ID
     * @return 조기 낙찰 처리된 경매 상세 정보
     */
    @PostMapping("/{auctionId}/award-current")
    public ResponseEntity<AuctionDetailResponse> awardCurrent(
            @PathVariable Long auctionId
    ) {
        return ResponseEntity.ok(auctionCommandService.awardCurrent(AuthContextHolder.getUserId(), auctionId));
    }
}
