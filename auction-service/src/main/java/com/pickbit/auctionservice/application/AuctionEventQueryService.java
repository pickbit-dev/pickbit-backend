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

    public List<AuctionEventResponse> getEvents(Long auctionId, Long afterEventId, Integer limit) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new AuctionNotFoundException(auctionId);
        }

        PageRequest pageRequest = PageRequest.of(0, normalizeLimit(limit));
        if (afterEventId != null) {
            return auctionEventRepository.findByAuctionIdAndIdGreaterThanOrderByIdAsc(auctionId, afterEventId, pageRequest)
                    .map(AuctionEventResponse::from)
                    .getContent();
        }

        List<AuctionEvent> events = new ArrayList<>(auctionEventRepository
                .findByAuctionIdOrderByIdDesc(auctionId, pageRequest)
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
