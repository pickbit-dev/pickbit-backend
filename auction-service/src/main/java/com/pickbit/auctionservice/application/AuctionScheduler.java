package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.application.event.AuctionCacheEvictEvent;
import com.pickbit.auctionservice.application.event.AuctionRealtimeEvent;
import com.pickbit.auctionservice.config.BidArbiterProperties;
import com.pickbit.auctionservice.infrastructure.redis.AuctionState;
import com.pickbit.auctionservice.infrastructure.redis.AuctionStateStore;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private static final String BID_LOCK_KEY = "auction:bid:lock:";

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedissonClient redissonClient;
    private final OutboxRecorder outboxRecorder;
    private final AuctionCompleter auctionCompleter;
    private final AuctionEventRecorder auctionEventRecorder;
    private final BidArbiterProperties arbiterProperties;
    private final AuctionStateStore stateStore;
    private final AuctionSequenceAllocator sequenceAllocator;

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
            // 입찰 중재가 Redis 에서 이뤄지므로 활성화와 동시에 상태를 올려둔다.
            // (누락되더라도 첫 입찰에서 NOT_LOADED 를 받고 복구된다)
            if (arbiterProperties.isEnabled()) {
                stateStore.hydrate(auction, sequenceAllocator.lastPersistedSequence(auction.getId()));
            }
            recordProductStatusUpdate(auction.getProductId(), "IN_AUCTION", "AUCTION_STARTED", auction.getId());
            publishAuctionEvent(auction, AuctionBidEvent.ofStarted(auction.getId(), auction.getCurrentPrice()));
            eventPublisher.publishEvent(new AuctionCacheEvictEvent(auction.getId()));
        });
        log.info("경매 활성화: {}건", toActivate.size());
    }

    @Transactional
    public void closeExpiredAuctions(LocalDateTime now) {
        List<Auction> expired = auctionRepository.findExpiredActiveAuctions(now);
        if (expired.isEmpty()) return;

        int closed = 0;
        for (Auction auction : expired) {
            boolean done = arbiterProperties.isEnabled()
                    ? closeAuctionViaArbiter(auction.getId())
                    : closeAuctionWithLock(auction.getId());
            if (done) {
                closed++;
            }
        }
        log.info("경매 종료 처리: {}건", closed);
    }

    /**
     * 중재 경로에서는 Redis 상태를 먼저 닫아 추가 입찰을 막고, 스트림에 남은 입찰이 DB에 반영될
     * 때까지 기다린 뒤 낙찰자를 정합니다. 기다리지 않으면 아직 기록되지 않은 최고 입찰을
     * 놓친 채로 낙찰자를 뽑게 됩니다.
     */
    private boolean closeAuctionViaArbiter(Long auctionId) {
        stateStore.close(auctionId);

        if (!waitForDrain(auctionId)) {
            log.error("입찰 반영 대기 시간을 초과했습니다. 이번 주기에는 종료하지 않습니다. auctionId={}", auctionId);
            return false;
        }

        Auction fresh = auctionRepository.findById(auctionId).orElse(null);
        if (fresh == null || fresh.getAuctionStatus() != AuctionStatus.ACTIVE) {
            return false;
        }

        closeAuction(fresh);
        stateStore.remove(auctionId);
        return true;
    }

    /** 스트림에 남은 입찰이 전부 DB에 반영될 때까지 기다린다. */
    private boolean waitForDrain(Long auctionId) {
        long deadline = System.currentTimeMillis() + arbiterProperties.getDrainTimeoutMillis();
        while (System.currentTimeMillis() < deadline) {
            AuctionState state = stateStore.read(auctionId);
            if (state == null || state.isFullyPersisted()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean closeAuctionWithLock(Long auctionId) {
        RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, TimeUnit.SECONDS);
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
            auctionCompleter.completeWithWinner(auction, winner, "AUCTION_ENDED_SOLD");

            publishAuctionEvent(auction, AuctionBidEvent.ofEnded(auction.getId(), winner.getBidderNickname(), winner.getAmount()));
        } else {
            auction.endWithNoBids();

            publishAuctionEvent(auction, AuctionBidEvent.ofEndedNoBids(auction.getId()));
            recordProductStatusUpdate(auction.getProductId(), "ACTIVE", "AUCTION_ENDED_NO_BIDS", auction.getId());
        }

        eventPublisher.publishEvent(new AuctionCacheEvictEvent(auction.getId()));
    }


    private void recordProductStatusUpdate(Long productId, String status, String reason, Long auctionId) {
        outboxRecorder.productStatusUpdateEvent(productId, status, reason, auctionId);
    }

    private void publishAuctionEvent(Auction auction, AuctionBidEvent event) {
        eventPublisher.publishEvent(new AuctionRealtimeEvent(
                auction.getId(), auctionEventRecorder.record(auction, event)));
    }
}
