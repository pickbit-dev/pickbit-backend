package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.response.BidResponse;
import com.pickbit.auctionservice.application.mapper.BidMapper;
import com.pickbit.auctionservice.exception.AuctionNotFoundException;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidQueryService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final BidMapper bidMapper;

    public Page<BidResponse> getBidHistory(Long auctionId, Pageable pageable) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new AuctionNotFoundException(auctionId);
        }
        return bidRepository.findByAuctionIdOrderByAmountDesc(auctionId, pageable)
                .map(bidMapper::toResponse);
    }

    public Page<BidResponse> getBidTimeline(Long auctionId, Pageable pageable) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new AuctionNotFoundException(auctionId);
        }
        return bidRepository.findByAuctionIdOrderByBidTimeDesc(auctionId, pageable)
                .map(bidMapper::toResponse);
    }
}
