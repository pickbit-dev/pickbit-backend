package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.response.AuctionEventResponse;
import com.pickbit.auctionservice.domain.AuctionEvent;
import com.pickbit.auctionservice.exception.AuctionNotFoundException;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionEventRepository;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionEventQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final AuctionRepository auctionRepository;
    private final AuctionEventRepository auctionEventRepository;

    /**
     * 경매 이벤트를 조회합니다.
     *
     * <p>{@code afterEventId}는 DB id 가 아니라 경매 내 순번입니다. 입찰이 비동기로 기록되면서
     * auto-increment 순서가 실제 입찰 순서를 보장하지 못하게 됐기 때문입니다.
     * WebSocket 으로 나가는 실시간 이벤트도 같은 순번을 싣고 있어 클라이언트는 그대로 이어서 쓸 수 있습니다.
     */
    public List<AuctionEventResponse> getEvents(Long auctionId, Long afterEventId, Integer limit) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new AuctionNotFoundException(auctionId);
        }

        PageRequest pageRequest = PageRequest.of(0, normalizeLimit(limit));
        if (afterEventId != null) {
            return auctionEventRepository
                    .findByAuctionIdAndSequenceGreaterThanOrderBySequenceAsc(auctionId, afterEventId, pageRequest)
                    .map(AuctionEventResponse::from)
                    .getContent();
        }

        List<AuctionEvent> events = new ArrayList<>(auctionEventRepository
                .findByAuctionIdOrderBySequenceDesc(auctionId, pageRequest)
                .getContent());
        Collections.reverse(events);
        return events.stream()
                .map(AuctionEventResponse::from)
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.clamp(limit, 1, MAX_LIMIT);
    }
}
