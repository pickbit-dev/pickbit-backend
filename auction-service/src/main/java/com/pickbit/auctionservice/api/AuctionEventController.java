package com.pickbit.auctionservice.api;

import com.pickbit.auctionservice.api.dto.response.AuctionEventResponse;
import com.pickbit.auctionservice.application.AuctionEventQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 경매 실시간 이벤트 히스토리 조회 API 컨트롤러.
 *
 * <p>WebSocket 재연결 또는 초기 진입 시 누락된 경매 이벤트를 조회하는 기능을 제공합니다.
 */
@Tag(name = "Auction Event", description = "경매 실시간 이벤트 조회 API")
@RestController
@RequestMapping("/api/auctions/{auctionId}/events")
@RequiredArgsConstructor
public class AuctionEventController {

    private final AuctionEventQueryService auctionEventQueryService;

    /**
     * 특정 경매의 저장된 실시간 이벤트를 조회합니다.
     *
     * @param auctionId 조회할 경매 ID
     * @param afterEventId 이 이벤트 ID 이후의 이벤트만 조회할 기준 ID (선택)
     * @param limit 조회할 최대 이벤트 개수 (선택)
     * @return 경매 이벤트 목록
     */
    @GetMapping
    public ResponseEntity<List<AuctionEventResponse>> getEvents(
            @PathVariable Long auctionId,
            @RequestParam(required = false) Long afterEventId,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(auctionEventQueryService.getEvents(auctionId, afterEventId, limit));
    }
}
