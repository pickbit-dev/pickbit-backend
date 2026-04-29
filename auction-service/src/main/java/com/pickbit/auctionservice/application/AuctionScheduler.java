package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.Bid;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import com.pickbit.auctionservice.domain.enums.BidStatus;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private static final String AUCTION_TOPIC = "/topic/auctions/";
    private static final String BID_LOCK_KEY = "auction:bid:lock:";

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedissonClient redissonClient;
    private final OutboxRecorder outboxRecorder;

    @Scheduled(cron = "${auction.scheduler.cron}")
    @SchedulerLock(name = "processAuctions", lockAtMostFor = "PT30S", lockAtLeastFor = "PT5S")
    @Transactional
    public void processAuctions() {
        LocalDateTime now = LocalDateTime.now();
        activateScheduledAuctions(now);
        closeExpiredAuctions(now);
    }

    @Transactional
    public void activateScheduledAuctions(LocalDateTime now) {
        List<Auction> toActivate = auctionRepository.findScheduledAuctionsToActivate(now);
        toActivate.forEach(auction -> {
            auction.activate();
            recordProductStatusUpdate(auction.getProductId(), "IN_AUCTION", "AUCTION_STARTED", auction.getId());
        });
        log.info("경매 활성화: {}건", toActivate.size());
    }

    @Transactional
    public void closeExpiredAuctions(LocalDateTime now) {
        List<Auction> expired = auctionRepository.findExpiredActiveAuctions(now);
        if (expired.isEmpty()) return;

        int closed = 0;
        for (Auction auction : expired) {
            if (closeAuctionWithLock(auction.getId())) {
                closed++;
            }
        }
        log.info("경매 종료 처리: {}건", closed);
    }

    private boolean closeAuctionWithLock(Long auctionId) {
        RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("경매 종료 락 획득 실패. auctionId={}", auctionId);
                return false;
            }

            Auction fresh = auctionRepository.findById(auctionId).orElse(null);
            if (fresh == null || fresh.getAuctionStatus() != AuctionStatus.ACTIVE) {
                return false;
            }

            closeAuction(fresh);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("경매 종료 락 대기 중 인터럽트. auctionId={}", auctionId, e);
            return false;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void closeAuction(Auction auction) {
        Optional<Bid> topBid = bidRepository.findTopByAuctionIdOrderByAmountDesc(auction.getId());

        if (topBid.isPresent()) {
            Bid winner = topBid.get();
            bidRepository.updateAllActiveBidsByAuctionId(auction.getId(), BidStatus.OUTBID);
            winner.markWinning();
            auction.complete(winner.getBidderNickname(), winner.getAmount());

            notifyAuctionEnded(auction.getId(), AuctionBidEvent.ofEnded(winner.getBidderNickname(), winner.getAmount()));
            recordProductStatusUpdate(auction.getProductId(), "AUCTION_COMPLETED", "AUCTION_ENDED_SOLD", auction.getId());
        } else {
            auction.endWithNoBids();

            notifyAuctionEnded(auction.getId(), AuctionBidEvent.ofEndedNoBids());
            recordProductStatusUpdate(auction.getProductId(), "ACTIVE", "AUCTION_ENDED_NO_BIDS", auction.getId());
        }
    }

    private void recordProductStatusUpdate(Long productId, String status, String reason, Long auctionId) {
        outboxRecorder.record(
                "Product",
                String.valueOf(productId),
                "product.status.update_requested",
                "UPDATE",
                Map.of("productId", productId, "status", status, "reason", reason, "auctionId", auctionId)
        );
    }

    private void notifyAuctionEnded(Long auctionId, AuctionBidEvent event) {
        try {
            messagingTemplate.convertAndSend(AUCTION_TOPIC + auctionId, event);
        } catch (Exception e) {
            log.error("경매 종료 WebSocket 알림 실패. auctionId={}", auctionId, e);
        }
    }
}
