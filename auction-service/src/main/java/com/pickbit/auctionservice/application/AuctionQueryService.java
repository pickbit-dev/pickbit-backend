package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.response.AuctionDetailResponse;
import com.pickbit.auctionservice.api.dto.response.AuctionSummaryResponse;
import com.pickbit.auctionservice.application.mapper.AuctionMapper;
import com.pickbit.auctionservice.config.CacheConfig;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.auctionservice.exception.AuctionNotFoundException;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.library.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionQueryService {

    private final AuctionRepository auctionRepository;
    private final AuctionMapper auctionMapper;

    public PageResponse<AuctionSummaryResponse> getAuctions(AuctionStatus status, Pageable pageable) {
        var page = status != null
                ? auctionRepository.findByAuctionStatus(status, pageable)
                : auctionRepository.findAll(pageable);
        return PageResponse.from(page.map(auctionMapper::toSummaryResponse));
    }

    @Cacheable(value = CacheConfig.AUCTION_CACHE, key = "#auctionId")
    public AuctionDetailResponse getAuction(Long auctionId) {
        return auctionMapper.toDetailResponse(findAuction(auctionId));
    }

    public AuctionDetailResponse getLatestAuctionByProductId(Long productId) {
        Auction auction = auctionRepository.findFirstByProductIdOrderByCreatedDateDescIdDesc(productId)
                .orElseThrow(() -> AuctionNotFoundException.byProductId(productId));
        return auctionMapper.toDetailResponse(auction);
    }

    private Auction findAuction(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));
    }
}
