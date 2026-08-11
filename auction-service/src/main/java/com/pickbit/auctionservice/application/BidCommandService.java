package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.request.BidCreateRequest;
import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.api.dto.response.BidResponse;
import com.pickbit.auctionservice.application.event.AuctionCacheEvictEvent;
import com.pickbit.auctionservice.application.event.AuctionRealtimeEvent;
import com.pickbit.auctionservice.config.BidArbiterProperties;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.enums.BidStatus;
import com.pickbit.auctionservice.exception.AuctionNotFoundException;
import com.pickbit.auctionservice.exception.InvalidAuctionStatusException;
import com.pickbit.auctionservice.exception.InvalidBidAmountException;
import com.pickbit.auctionservice.exception.UnauthorizedAuctionAccessException;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.redis.BidArbitrationResult;
import com.pickbit.auctionservice.infrastructure.redis.BidArbiter;
import com.pickbit.auctionservice.infrastructure.redis.AuctionStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidCommandService {

    private static final String BID_LOCK_KEY = "auction:bid:lock:";

    private final RedissonClient redissonClient;
    private final BidProcessor bidProcessor;
    private final TransactionTemplate transactionTemplate;

    private final BidArbiterProperties arbiterProperties;
    private final BidArbiter bidArbiter;
    private final AuctionStateStore stateStore;
    private final AuctionRepository auctionRepository;
    private final AuctionSequenceAllocator sequenceAllocator;
    private final ApplicationEventPublisher eventPublisher;

    public BidResponse placeBid(Long bidderUserId, String bidderNickname, Long auctionId, BidCreateRequest request) {
        if (arbiterProperties.isEnabled()) {
            return placeBidViaArbiter(bidderUserId, bidderNickname, auctionId, request);
        }
        return placeBidWithLock(bidderUserId, bidderNickname, auctionId, request);
    }

    /**
     * Redis 안에서 검증과 현재가 갱신을 원자적으로 끝내고, DB 기록은 스트림으로 넘깁니다.
     *
     * <p>왕복 한 번으로 끝나므로 한 경매의 입찰이 DB 트랜잭션 길이만큼 직렬화되지 않습니다.
     * 실시간 이벤트도 DB 기록을 기다리지 않고 즉시 발행합니다.
     */
    private BidResponse placeBidViaArbiter(
            Long bidderUserId, String bidderNickname, Long auctionId, BidCreateRequest request) {

        LocalDateTime bidTime = LocalDateTime.now();
        long nowEpochMs = System.currentTimeMillis();

        BidArbitrationResult result =
                bidArbiter.placeBid(auctionId, bidderUserId, bidderNickname, request.bidAmount(), nowEpochMs);

        if (result.needsHydration()) {
            // Redis 가 재시작됐거나 아직 로드되지 않은 경매다. DB에서 복구하고 한 번만 재시도한다.
            hydrateFromDatabase(auctionId);
            result = bidArbiter.placeBid(auctionId, bidderUserId, bidderNickname, request.bidAmount(), nowEpochMs);
        }

        if (!result.accepted()) {
            throw toException(result, auctionId);
        }

        // DB 기록을 기다리지 않고 즉시 실시간 이벤트를 내보낸다.
        eventPublisher.publishEvent(new AuctionRealtimeEvent(auctionId, AuctionBidEvent
                .ofNewBid(auctionId, null, request.bidAmount(), bidderNickname, bidTime)
                .withEventMetadata(result.sequence(), bidTime)));

        if (result.ended()) {
            // 즉시 구매가 도달로 경매가 함께 닫혔다. 종료 이벤트도 바로 알린다.
            eventPublisher.publishEvent(new AuctionRealtimeEvent(auctionId, AuctionBidEvent
                    .ofEnded(auctionId, bidderNickname, request.bidAmount())
                    .withEventMetadata(result.endedSequence(), bidTime)));
        }

        eventPublisher.publishEvent(new AuctionCacheEvictEvent(auctionId));

        // 아직 DB에 기록되기 전이므로 입찰 ID는 없다. 클라이언트는 순번으로 이벤트를 잇는다.
        return new BidResponse(
                null, auctionId, bidderNickname, request.bidAmount(), bidTime, BidStatus.ACTIVE);
    }

    private void hydrateFromDatabase(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));
        stateStore.hydrate(auction, sequenceAllocator.lastPersistedSequence(auctionId));
    }

    private RuntimeException toException(BidArbitrationResult result, Long auctionId) {
        return switch (result.reason()) {
            case "NOT_ACTIVE" -> new InvalidAuctionStatusException("ACTIVE 상태의 경매에만 입찰할 수 있습니다.");
            case "TIME_OVER" -> new InvalidAuctionStatusException("종료된 경매에는 입찰할 수 없습니다.");
            case "SELLER" -> new UnauthorizedAuctionAccessException();
            case "TOO_LOW" -> new InvalidBidAmountException(
                    "입찰가는 %s 이상이어야 합니다.".formatted(result.minimumRequired()));
            case BidArbitrationResult.NOT_LOADED -> new InvalidAuctionStatusException("경매 정보를 불러오지 못했습니다.");
            default -> {
                log.warn("알 수 없는 입찰 거절 사유 | auctionId={} | reason={}", auctionId, result.reason());
                yield new InvalidAuctionStatusException("입찰을 처리하지 못했습니다.");
            }
        };
    }

    /**
     * 중재를 껐을 때 쓰는 기존 경로입니다. 경매 단위 분산락을 DB 트랜잭션 전체 동안 보유하므로
     * 한 경매의 입찰이 직렬화됩니다.
     */
    private BidResponse placeBidWithLock(
            Long bidderUserId, String bidderNickname, Long auctionId, BidCreateRequest request) {
        RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
        try {
            boolean acquired = lock.tryLock(5, TimeUnit.SECONDS);
            if (!acquired) {
                throw new InvalidAuctionStatusException("입찰 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }
            return transactionTemplate.execute(status -> bidProcessor.process(bidderUserId, bidderNickname, auctionId, request));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidAuctionStatusException("입찰 처리 중 오류가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
