package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.request.AuctionCreateRequest;
import com.pickbit.auctionservice.api.dto.response.AuctionDetailResponse;
import com.pickbit.auctionservice.api.dto.response.AuctionSummaryResponse;
import com.pickbit.auctionservice.application.mapper.AuctionMapper;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.auctionservice.exception.AuctionNotFoundException;
import com.pickbit.auctionservice.exception.InvalidAuctionStatusException;
import com.pickbit.auctionservice.exception.InvalidProductForAuctionException;
import com.pickbit.auctionservice.exception.UnauthorizedAuctionAccessException;
import com.pickbit.auctionservice.infrastructure.client.ProductServiceClient;
import com.pickbit.auctionservice.infrastructure.client.dto.ProductResponse;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.library.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final ProductServiceClient productServiceClient;
    private final AuctionMapper auctionMapper;

    @Transactional
    public AuctionDetailResponse createAuction(String sellerNickname, AuctionCreateRequest request) {
        ProductResponse product = productServiceClient.getProduct(request.productId());

        if (!"ACTIVE".equals(product.productStatus())) {
            throw new InvalidProductForAuctionException("경매 등록 가능한 상태의 상품이 아닙니다. 현재 상태: " + product.productStatus());
        }
        if (!"AUCTION".equals(product.listingType())) {
            throw new InvalidProductForAuctionException("경매 판매 유형으로 등록된 상품만 경매를 진행할 수 있습니다.");
        }
        if (!product.sellerNickname().equals(sellerNickname)) {
            throw new UnauthorizedAuctionAccessException();
        }
        if (auctionRepository.existsByProductIdAndAuctionStatusIn(
                request.productId(), List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE))) {
            throw new InvalidAuctionStatusException("해당 상품에 이미 진행 중이거나 예정된 경매가 있습니다.");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new InvalidAuctionStatusException("경매 종료 시각은 시작 시각보다 늦어야 합니다.");
        }

        Auction auction = Auction.builder()
                .productId(request.productId())
                .productName(product.name())
                .productThumbnailUrl(product.thumbnailUrl())
                .sellerNickname(sellerNickname)
                .startingPrice(request.startingPrice())
                .currentPrice(request.startingPrice())
                .buyNowPrice(request.buyNowPrice())
                .minimumBidIncrement(request.minimumBidIncrement())
                .auctionStatus(AuctionStatus.SCHEDULED)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();

        return auctionMapper.toDetailResponse(auctionRepository.save(auction));
    }

    public PageResponse<AuctionSummaryResponse> getAuctions(AuctionStatus status, Pageable pageable) {
        var page = status != null
                ? auctionRepository.findByAuctionStatus(status, pageable)
                : auctionRepository.findAll(pageable);
        return PageResponse.from(page.map(auctionMapper::toSummaryResponse));
    }

    public AuctionDetailResponse getAuction(Long auctionId) {
        return auctionMapper.toDetailResponse(findAuction(auctionId));
    }

    @Transactional
    public void cancelAuction(String sellerNickname, Long auctionId) {
        Auction auction = findAuction(auctionId);

        if (!auction.getSellerNickname().equals(sellerNickname)) {
            throw new UnauthorizedAuctionAccessException();
        }
        if (auction.getAuctionStatus() != AuctionStatus.SCHEDULED) {
            throw new InvalidAuctionStatusException("SCHEDULED 상태의 경매만 취소할 수 있습니다.");
        }

        auction.cancel();
    }

    private Auction findAuction(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));
    }
}
