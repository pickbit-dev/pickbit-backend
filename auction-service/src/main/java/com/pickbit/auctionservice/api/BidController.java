package com.pickbit.auctionservice.api;

import com.pickbit.auctionservice.api.dto.request.BidCreateRequest;
import com.pickbit.auctionservice.api.dto.response.BidResponse;
import com.pickbit.auctionservice.application.BidCommandService;
import com.pickbit.auctionservice.application.BidQueryService;
import com.pickbit.library.auth.AuthContextHolder;
import com.pickbit.library.dto.PageResponse;
import com.pickbit.library.dto.PageableRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 입찰 API 컨트롤러.
 *
 * <p>특정 경매에 대한 입찰 제출 및 입찰 내역 조회 기능을 제공합니다.
 * 실시간 입찰 결과는 WebSocket {@code /topic/auctions/{auctionId}} 채널을 구독하여 수신할 수 있습니다.
 */
@Tag(name = "Bid", description = "입찰 API")
@RestController
@RequestMapping("/api/auctions/{auctionId}/bids")
@RequiredArgsConstructor
public class BidController {

    private final BidCommandService bidCommandService;
    private final BidQueryService bidQueryService;

    /**
     * 입찰을 제출합니다.
     *
     * <p>{@code ACTIVE} 상태의 경매에만 입찰할 수 있습니다.
     * 입찰가는 현재가 + 최소 입찰 단위 이상이어야 하며,
     * 판매자 본인은 입찰할 수 없습니다.
     * 즉시 구매가 이상의 금액으로 입찰 시 경매가 즉시 종료됩니다.
     *
     * @param auctionId 경매 ID
     * @param request   입찰 요청 데이터
     * @return 입찰 결과 (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<BidResponse> placeBid(
            @PathVariable Long auctionId,
            @Valid @RequestBody BidCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bidCommandService.placeBid(
                        AuthContextHolder.getUserId(), AuthContextHolder.getNickname(), auctionId, request));
    }

    /**
     * 경매의 입찰 내역을 금액 내림차순으로 페이징 조회합니다.
     *
     * @param auctionId       경매 ID
     * @param pageableRequest 페이징 및 정렬 조건
     * @return 입찰 내역 목록 (페이징)
     */
    @GetMapping
    public ResponseEntity<PageResponse<BidResponse>> getBidHistory(
            @PathVariable Long auctionId,
            @ModelAttribute PageableRequest pageableRequest
    ) {
        return ResponseEntity.ok(PageResponse.from(bidQueryService.getBidHistory(auctionId, pageableRequest.toPageable(20))));
    }

    /**
     * 경매의 입찰 내역을 최신 입찰순으로 페이징 조회합니다.
     *
     * @param auctionId       경매 ID
     * @param pageableRequest 페이징 및 정렬 조건
     * @return 입찰 타임라인 목록 (페이징)
     */
    @GetMapping("/timeline")
    public ResponseEntity<PageResponse<BidResponse>> getBidTimeline(
            @PathVariable Long auctionId,
            @ModelAttribute PageableRequest pageableRequest
    ) {
        return ResponseEntity.ok(PageResponse.from(bidQueryService.getBidTimeline(auctionId, pageableRequest.toPageable(20))));
    }
}
