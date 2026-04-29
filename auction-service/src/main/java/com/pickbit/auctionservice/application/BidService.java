package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.request.BidCreateRequest;
import com.pickbit.auctionservice.api.dto.response.BidResponse;
import com.pickbit.auctionservice.application.mapper.BidMapper;
import com.pickbit.auctionservice.exception.AuctionNotFoundException;
import com.pickbit.auctionservice.exception.InvalidAuctionStatusException;
import com.pickbit.auctionservice.infrastructure.persistence.AuctionRepository;
import com.pickbit.auctionservice.infrastructure.persistence.BidRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class BidService {

    private static final String BID_LOCK_KEY = "auction:bid:lock:";
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final BidMapper bidMapper;
    private final RedissonClient redissonClient;
    private final BidProcessor bidProcessor;
    private final TransactionTemplate transactionTemplate;

    public BidResponse placeBid(String bidderNickname, Long auctionId, BidCreateRequest request) {
        RLock lock = redissonClient.getLock(BID_LOCK_KEY + auctionId);
        try {
            boolean acquired = lock.tryLock(5, TimeUnit.SECONDS);
            if (!acquired) {
                throw new InvalidAuctionStatusException("입찰 처리 중입니다. 잠시 후 다시 시도해주세요.");
            }
            return transactionTemplate.execute(status -> bidProcessor.process(bidderNickname, auctionId, request));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidAuctionStatusException("입찰 처리 중 오류가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<BidResponse> getBidHistory(Long auctionId, Pageable pageable) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new AuctionNotFoundException(auctionId);
        }
        return bidRepository.findByAuctionIdOrderByAmountDesc(auctionId, pageable)
                .map(bidMapper::toResponse);
    }
}
