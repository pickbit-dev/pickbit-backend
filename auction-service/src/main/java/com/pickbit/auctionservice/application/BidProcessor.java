package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.request.BidCreateRequest;
import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.api.dto.response.BidResponse;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Transactional
public class BidProcessor {

    private static final String AUCTION_TOPIC = "/topic/auctions/";

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final BidMapper bidMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final OutboxRecorder outboxRecorder;

    public BidResponse process(String bidderNickname, Long auctionId, BidCreateRequest request) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));

        if (auction.getAuctionStatus() != AuctionStatus.ACTIVE) {
            throw new InvalidAuctionStatusException("ACTIVE 상태의 경매에만 입찰할 수 있습니다.");
        }
        if (auction.getEndTime() != null && auction.getEndTime().isBefore(LocalDateTime.now())) {
            throw new InvalidAuctionStatusException("종료된 경매에는 입찰할 수 없습니다.");
        }
        if (auction.getSellerNickname().equals(bidderNickname)) {
            throw new UnauthorizedAuctionAccessException();
        }

        boolean isFirstBid = bidRepository.findTopByAuctionIdOrderByAmountDesc(auctionId).isEmpty();
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
            bid.markWinning();
            auction.complete(bidderNickname, request.bidAmount());

            messagingTemplate.convertAndSend(
                    AUCTION_TOPIC + auctionId,
                    AuctionBidEvent.ofEnded(bidderNickname, request.bidAmount())
            );
            outboxRecorder.record(
                    "Product",
                    String.valueOf(auction.getProductId()),
                    "product.status.update_requested",
                    "UPDATE",
                    Map.of("productId", auction.getProductId(), "status", "AUCTION_COMPLETED", "reason", "BUY_NOW_COMPLETED", "auctionId", auction.getId())
            );
        } else {
            messagingTemplate.convertAndSend(
                    AUCTION_TOPIC + auctionId,
                    AuctionBidEvent.ofNewBid(request.bidAmount(), bidderNickname, now)
            );
        }

        return bidMapper.toResponse(bid);
    }
}
