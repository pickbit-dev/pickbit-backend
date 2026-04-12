package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.Bid;
import com.pickbit.auctionservice.domain.enums.BidStatus;
import com.pickbit.auctionservice.infrastructure.client.ProductServiceClient;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private static final String AUCTION_TOPIC = "/topic/auctions/";

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ProductServiceClient productServiceClient;
    private final SimpMessagingTemplate messagingTemplate;

    @Scheduled(cron = "${auction.scheduler.cron}")
    @Transactional
    public void processAuctions() {
        LocalDateTime now = LocalDateTime.now();
        activateScheduledAuctions(now);
        closeExpiredAuctions(now);
    }

    @Transactional
    public void activateScheduledAuctions(LocalDateTime now) {
        List<Auction> toActivate = auctionRepository.findScheduledAuctionsToActivate(now);
        toActivate.forEach(Auction::activate);
        log.info("경매 활성화: {}건", toActivate.size());
    }

    @Transactional
    public void closeExpiredAuctions(LocalDateTime now) {
        List<Auction> expired = auctionRepository.findExpiredActiveAuctions(now);
        if (expired.isEmpty()) return;

        for (Auction auction : expired) {
            closeAuction(auction);
        }
        log.info("경매 종료 처리: {}건", expired.size());
    }

    private void closeAuction(Auction auction) {
        Optional<Bid> topBid = bidRepository.findTopByAuctionIdOrderByAmountDesc(auction.getId());

        if (topBid.isPresent()) {
            Bid winner = topBid.get();
            bidRepository.updateAllActiveBidsByAuctionId(auction.getId(), BidStatus.OUTBID);
            winner.markWinning();
            auction.complete(winner.getBidderNickname(), winner.getAmount());

            notifyAuctionEnded(auction.getId(), AuctionBidEvent.ofEnded(winner.getBidderNickname(), winner.getAmount()));
            productServiceClient.updateProductStatus(auction.getProductId(), "AUCTION_COMPLETED");
        } else {
            auction.endWithNoBids();

            notifyAuctionEnded(auction.getId(), AuctionBidEvent.ofEndedNoBids());
            productServiceClient.updateProductStatus(auction.getProductId(), "ACTIVE");
        }
    }

    private void notifyAuctionEnded(Long auctionId, AuctionBidEvent event) {
        try {
            messagingTemplate.convertAndSend(AUCTION_TOPIC + auctionId, event);
        } catch (Exception e) {
            log.error("경매 종료 WebSocket 알림 실패. auctionId={}", auctionId, e);
        }
    }
}
