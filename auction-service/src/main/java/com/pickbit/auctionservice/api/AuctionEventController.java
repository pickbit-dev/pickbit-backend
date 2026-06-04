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

@Tag(name = "Auction Event", description = "경매 실시간 이벤트 조회 API")
@RestController
@RequestMapping("/api/auctions/{auctionId}/events")
@RequiredArgsConstructor
public class AuctionEventController {

    private final AuctionEventQueryService auctionEventQueryService;

    @GetMapping
    public ResponseEntity<List<AuctionEventResponse>> getEvents(
            @PathVariable Long auctionId,
            @RequestParam(required = false) Long afterEventId,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(auctionEventQueryService.getEvents(auctionId, afterEventId, limit));
    }
}
