package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.request.AuctionCreateRequest;
import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.api.dto.response.AuctionDetailResponse;
import com.pickbit.auctionservice.application.event.AuctionCacheEvictEvent;
import com.pickbit.auctionservice.application.event.AuctionRealtimeEvent;
import com.pickbit.auctionservice.application.mapper.AuctionMapper;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.Bid;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.auctionservice.domain.enums.BidStatus;
import com.pickbit.auctionservice.exception.AuctionNotFoundException;
import com.pickbit.auctionservice.exception.InvalidAuctionStatusException;
import com.pickbit.auctionservice.exception.InvalidProductForAuctionException;
import com.pickbit.auctionservice.exception.UnauthorizedAuctionAccessException;
import com.pickbit.auctionservice.infrastructure.client.ProductServiceClient;
import com.pickbit.auctionservice.infrastructure.client.dto.ProductResponse;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class AuctionCommandService {

    private static final String BID_LOCK_KEY = "auction:bid:lock:";

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ProductServiceClient productServiceClient;
    private final AuctionMapper auctionMapper;
    private final OutboxRecorder outboxRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redissonClient;
    private final AuctionEventRecorder auctionEventRecorder;
    private final AuctionCompleter auctionCompleter;

    public AuctionDetailResponse createAuction(Long sellerUserId, String sellerNickname, AuctionCreateRequest request) {

        ProductResponse product = productServiceClient.getProduct(request.productId());

        if (!"ACTIVE".equals(product.productStatus())) {
            throw new InvalidProductForAuctionException("경매 등록 가능한 상태의 상품이 아닙니다. 현재 상태: " + product.productStatus());
        }
        if (!sellerUserId.equals(product.sellerUserId())) {
            throw new UnauthorizedAuctionAccessException();
        }
        if (auctionRepository.existsByProductIdAndAuctionStatusIn(
                request.productId(), List.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE))) {
            throw new InvalidAuctionStatusException("해당 상품에 이미 진행 중이거나 예정된 경매가 있습니다.");
        }
        if (!request.endTime().isAfter(request.startTime())) {
            throw new InvalidAuctionStatusException("경매 종료 시각은 시작 시각보다 늦어야 합니다.");
        }

        productServiceClient.reserveForAuction(request.productId(), sellerUserId);

        Auction auction = Auction.builder()
                .productId(request.productId())
                .productName(product.name())
                .productThumbnailUrl(product.thumbnailUrl())
                .sellerUserId(sellerUserId)
                .sellerNickname(product.sellerNickname())
                .startingPrice(request.startingPrice())
                .currentPrice(request.startingPrice())
                .buyNowPrice(request.buyNowPrice())
                .minimumBidIncrement(request.minimumBidIncrement())
                .auctionStatus(AuctionStatus.SCHEDULED)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .build();

        try {
            Auction saved = auctionRepository.saveAndFlush(auction);
            return auctionMapper.toDetailResponse(saved);
        } catch (RuntimeException e) {
            productServiceClient.releaseAuctionReservation(request.productId());
            throw e;
        }
    }

    public void cancelAuction(Long sellerUserId, Long auctionId) {
        RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(5, TimeUnit.SECONDS);
            if (!acquired) {
                throw new InvalidAuctionStatusException("경매 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }

            Auction auction = auctionRepository.findById(auctionId)
                    .orElseThrow(() -> new AuctionNotFoundException(auctionId));

            validateCancelable(sellerUserId, auction);

            auction.cancel();
            recordProductStatusUpdate(auction.getProductId(), "ACTIVE", "AUCTION_CANCELLED", auction.getId());
            eventPublisher.publishEvent(new AuctionRealtimeEvent(
                    auctionId, auctionEventRecorder.record(auction, AuctionBidEvent.ofCancelled(auctionId))));
            eventPublisher.publishEvent(new AuctionCacheEvictEvent(auctionId));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidAuctionStatusException("경매 취소 처리 중 오류가 발생했습니다.");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public AuctionDetailResponse awardCurrent(Long sellerUserId, Long auctionId) {
        RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(5, TimeUnit.SECONDS);
            if (!acquired) {
                throw new InvalidAuctionStatusException("경매 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }

            Auction auction = auctionRepository.findById(auctionId)
                    .orElseThrow(() -> new AuctionNotFoundException(auctionId));
            validateAwardable(sellerUserId, auction);

            Bid winner = bidRepository.findTopByAuctionIdAndBidStatusOrderByAmountDesc(auctionId, BidStatus.ACTIVE)
                    .orElseThrow(() -> new InvalidAuctionStatusException("입찰이 없는 경매는 조기 낙찰할 수 없습니다."));

            bidRepository.updateActiveBidsExceptWinnerByAuctionId(auctionId, winner.getId(), BidStatus.OUTBID);
            auctionCompleter.completeWithWinner(auction, winner, "AUCTION_AWARDED_EARLY");

            eventPublisher.publishEvent(new AuctionRealtimeEvent(
                    auctionId,
                    auctionEventRecorder.record(auction,
                            AuctionBidEvent.ofEnded(auctionId, winner.getBidderNickname(), winner.getAmount()))));
            eventPublisher.publishEvent(new AuctionCacheEvictEvent(auctionId));

            return auctionMapper.toDetailResponse(auction);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidAuctionStatusException("조기 낙찰 처리 중 오류가 발생했습니다.");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void validateAwardable(Long sellerUserId, Auction auction) {
        if (!auction.getSellerUserId().equals(sellerUserId)) {
            throw new UnauthorizedAuctionAccessException();
        }
        if (auction.getAuctionStatus() != AuctionStatus.ACTIVE) {
            throw new InvalidAuctionStatusException("ACTIVE 상태의 경매만 조기 낙찰할 수 있습니다.");
        }
    }

    private void validateCancelable(Long sellerUserId, Auction auction) {
        if (!auction.getSellerUserId().equals(sellerUserId)) {
            throw new UnauthorizedAuctionAccessException();
        }
        if (auction.getAuctionStatus() == AuctionStatus.SCHEDULED) {
            return;
        }
        if (auction.getAuctionStatus() == AuctionStatus.ACTIVE && !bidRepository.existsByAuctionId(auction.getId())) {
            return;
        }
        if (auction.getAuctionStatus() == AuctionStatus.ACTIVE) {
            throw new InvalidAuctionStatusException("입찰이 있는 ACTIVE 상태의 경매는 취소할 수 없습니다.");
        }
        throw new InvalidAuctionStatusException("SCHEDULED 또는 입찰이 없는 ACTIVE 상태의 경매만 취소할 수 있습니다.");
    }

    private void recordProductStatusUpdate(Long productId, String status, String reason, Long auctionId) {
        outboxRecorder.productStatusUpdateEvent(productId, status, reason, auctionId);
    }
}
