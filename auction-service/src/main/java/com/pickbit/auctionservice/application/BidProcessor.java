package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.request.BidCreateRequest;
import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.api.dto.response.BidResponse;
import com.pickbit.auctionservice.application.event.AuctionCacheEvictEvent;
import com.pickbit.auctionservice.application.event.AuctionRealtimeEvent;
import com.pickbit.auctionservice.application.mapper.BidMapper;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.Bid;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.auctionservice.domain.enums.BidStatus;
import com.pickbit.auctionservice.exception.AuctionNotFoundException;
import com.pickbit.auctionservice.exception.InvalidAuctionStatusException;
import com.pickbit.auctionservice.exception.InvalidBidAmountException;
import com.pickbit.auctionservice.exception.UnauthorizedAuctionAccessException;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Transactional
public class BidProcessor {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final BidMapper bidMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxRecorder outboxRecorder;
    private final AuctionCompleter auctionCompleter;
    private final AuctionEventRecorder auctionEventRecorder;

    public BidResponse process(Long bidderUserId, String bidderNickname, Long auctionId, BidCreateRequest request) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if (auction.getAuctionStatus() != AuctionStatus.ACTIVE) {
            throw new InvalidAuctionStatusException("ACTIVE 상태의 경매에만 입찰할 수 있습니다.");
        }
        if (auction.getEndTime() != null && auction.getEndTime().isBefore(LocalDateTime.now())) {
            throw new InvalidAuctionStatusException("종료된 경매에는 입찰할 수 없습니다.");
        }

        if (auction.getSellerUserId().equals(bidderUserId)) {
            throw new UnauthorizedAuctionAccessException();
        }

        // 존재 여부만 필요하다. findTopByAuctionIdOrderByAmountDesc 를 쓰면 그 경매의 입찰
        // 전체를 정렬하므로 입찰이 쌓일수록 입찰 처리가 느려진다.
        boolean isFirstBid = !bidRepository.existsByAuctionId(auctionId);
        boolean isValidAmount = isFirstBid
                ? request.bidAmount().compareTo(auction.getStartingPrice()) >= 0
                : request.bidAmount().compareTo(auction.getCurrentPrice().add(auction.getMinimumBidIncrement())) >= 0;

        if (!isValidAmount) {
            String message = isFirstBid
                    ? "첫 입찰은 시작가(%s) 이상이어야 합니다.".formatted(auction.getStartingPrice())
                    : "입찰가는 현재가(%s) + 최소 입찰 단위(%s) 이상이어야 합니다.".formatted(
                            auction.getCurrentPrice(), auction.getMinimumBidIncrement());
            throw new InvalidBidAmountException(message);
        }

        // 기존 ACTIVE 입찰을 OUTBID로 전환
        bidRepository.updateAllActiveBidsByAuctionId(auctionId, BidStatus.OUTBID);

        LocalDateTime now = LocalDateTime.now();
        Bid bid = Bid.builder()
                .auction(auction)
                .bidderUserId(bidderUserId)
                .bidderNickname(bidderNickname)
                .amount(request.bidAmount())
                .bidTime(now)
                .bidStatus(BidStatus.ACTIVE)
                .build();
        bidRepository.save(bid);

        auction.placeBid(request.bidAmount());

        // 즉시 구매가 충족 시 경매 즉시 종료
        if (auction.getBuyNowPrice() != null
                && request.bidAmount().compareTo(auction.getBuyNowPrice()) >= 0) {
            auctionCompleter.completeWithWinner(auction, bid, "BUY_NOW_COMPLETED");

            eventPublisher.publishEvent(new AuctionRealtimeEvent(
                    auctionId,
                    auctionEventRecorder.record(auction,
                            AuctionBidEvent.ofEnded(auctionId, bidderNickname, request.bidAmount()))
            ));
        } else {
            eventPublisher.publishEvent(new AuctionRealtimeEvent(
                    auctionId,
                    auctionEventRecorder.record(auction,
                            AuctionBidEvent.ofNewBid(auctionId, bid.getId(), request.bidAmount(), bidderNickname, now))
            ));
        }

        eventPublisher.publishEvent(new AuctionCacheEvictEvent(auctionId));

        return bidMapper.toResponse(bid);
    }
}
