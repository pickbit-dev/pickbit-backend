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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEvict(AuctionCacheEvictEvent event) {
        Cache cache = cacheManager.getCache(CacheConfig.AUCTION_CACHE);
        if (cache != null) {
            cache.evict(event.auctionId());
        }
    }
}
