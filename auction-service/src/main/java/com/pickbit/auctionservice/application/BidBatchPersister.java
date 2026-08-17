package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.response.AuctionBidEvent;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.AuctionEvent;
import com.pickbit.auctionservice.domain.Bid;
import com.pickbit.auctionservice.domain.enums.BidStatus;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionEventRepository;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import com.pickbit.auctionservice.infrastructure.redis.AuctionStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 스트림에서 읽어온 입찰들을 경매 단위로 묶어 한 트랜잭션에 기록합니다.
 *
 * <p>입찰마다 트랜잭션을 여는 대신 배치로 묶어 커밋 횟수를 줄이는 것이 목적입니다.
 * 스트림은 단일 소비자가 순서대로 읽으므로 같은 경매의 입찰은 항상 순번 순으로 도착합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidBatchPersister {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionEventRepository auctionEventRepository;
    private final AuctionEventRecorder auctionEventRecorder;
    private final AuctionCompleter auctionCompleter;
    private final AuctionStateStore stateStore;
    private final AuctionSequenceAllocator sequenceAllocator;

    @Transactional
    public void persist(List<BidRecord> records) {
        Map<Long, List<BidRecord>> byAuction = groupByAuction(records);

        byAuction.forEach((auctionId, bids) -> {
            Auction auction = auctionRepository.findById(auctionId).orElse(null);
            if (auction == null) {
                log.warn("존재하지 않는 경매의 입찰을 건너뜁니다. auctionId={}", auctionId);
                return;
            }
            persistAuctionBids(auction, bids);
        });
    }

    private void persistAuctionBids(Auction auction, List<BidRecord> bids) {
        // 이미 기록된 순번은 버린다. 워커는 DB 커밋 후에 XACK 하므로 그 사이에 죽으면
        // 같은 배치가 다시 배달되는데, 이 걸러내기가 없으면 입찰이 중복 INSERT 된다.
        long persistedSequence = sequenceAllocator.lastPersistedSequence(auction.getId());
        List<BidRecord> fresh = bids.stream()
                .filter(record -> record.sequence() > persistedSequence)
                .toList();

        if (fresh.isEmpty()) {
            log.info("이미 반영된 입찰이라 건너뜁니다. auctionId={} | persistedSequence={}",
                    auction.getId(), persistedSequence);
            return;
        }

        persistFreshBids(auction, fresh);
    }

    private void persistFreshBids(Auction auction, List<BidRecord> bids) {
        // 이 배치의 마지막 입찰만 ACTIVE 로 남기고 나머지는 전부 OUTBID 가 된다.
        BidRecord winning = bids.getLast();

        bidRepository.updateAllActiveBidsByAuctionId(auction.getId(), BidStatus.OUTBID);

        List<AuctionEvent> events = new ArrayList<>(bids.size());
        long lastSequence = winning.sequence();

        for (BidRecord record : bids) {
            Bid bid = bidRepository.save(Bid.builder()
                    .auction(auction)
                    .bidderUserId(record.bidderUserId())
                    .bidderNickname(record.bidderNickname())
                    .amount(record.amount())
                    .bidTime(record.bidTime())
                    .bidStatus(record.sequence() == winning.sequence() ? BidStatus.ACTIVE : BidStatus.OUTBID)
                    .build());

            events.add(auctionEventRecorder.build(
                    auction,
                    AuctionBidEvent.ofNewBid(
                            auction.getId(), bid.getId(), record.amount(),
                            record.bidderNickname(), record.bidTime()),
                    record.sequence()));

            // 즉시 구매가 도달로 Redis 에서 이미 닫힌 경매는 여기서 낙찰까지 확정한다.
            if (record.ended()) {
                auction.placeBid(record.amount());
                auctionCompleter.completeWithWinner(auction, bid, "BUY_NOW_COMPLETED");
                events.add(auctionEventRecorder.build(
                        auction,
                        AuctionBidEvent.ofEnded(auction.getId(), record.bidderNickname(), record.amount()),
                        record.endedSequence()));
                lastSequence = Math.max(lastSequence, record.endedSequence());
            }
        }

        if (!winning.ended()) {
            auction.placeBid(winning.amount());
        }

        auctionEventRepository.saveAll(events);
        stateStore.markPersisted(auction.getId(), lastSequence);
    }

    /** 경매별로 묶되 각 경매 안에서는 순번 오름차순을 보장한다. */
    private Map<Long, List<BidRecord>> groupByAuction(List<BidRecord> records) {
        Map<Long, List<BidRecord>> byAuction = new LinkedHashMap<>();
        for (BidRecord record : records) {
            byAuction.computeIfAbsent(record.auctionId(), id -> new ArrayList<>()).add(record);
        }
        byAuction.values().forEach(list -> list.sort(Comparator.comparingLong(BidRecord::sequence)));
        return byAuction;
    }
}
