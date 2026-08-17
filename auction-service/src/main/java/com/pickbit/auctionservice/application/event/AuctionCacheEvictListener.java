package com.pickbit.auctionservice.application.event;

import com.pickbit.auctionservice.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionCacheEvictListener {

    private final CacheManager cacheManager;

    // 중재 경로는 트랜잭션 없이 이벤트를 발행한다. fallbackExecution 이 없으면 무효화가
    // 조용히 건너뛰어져 캐시 TTL 동안 옛 가격이 그대로 응답된다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEvict(AuctionCacheEvictEvent event) {
        Cache cache = cacheManager.getCache(CacheConfig.AUCTION_CACHE);
        if (cache != null) {
            cache.evict(event.auctionId());
        }
    }
}
