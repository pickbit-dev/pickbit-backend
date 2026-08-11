package com.pickbit.productservice.application;

import com.pickbit.productservice.infrastructure.persistence.ProductRepository;
import com.pickbit.productservice.infrastructure.redis.ViewCountBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Redis 에 모아둔 조회수를 주기적으로 DB에 반영합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushScheduler {

    private final ViewCountBuffer buffer;
    private final ProductRepository productRepository;

    @Scheduled(cron = "${product.view-count.flush-cron:0 * * * * *}")
    @Transactional
    public void flush() {
        Map<Long, Long> counts = buffer.drain();
        if (counts.isEmpty()) {
            return;
        }

        int updated = 0;
        for (Map.Entry<Long, Long> entry : counts.entrySet()) {
            // 삭제된 상품이면 0건이 갱신된다. 조회수는 부가 정보라 그대로 버린다.
            updated += productRepository.addViewCount(entry.getKey(), entry.getValue());
        }
        log.info("조회수 반영: 상품 {}건", updated);
    }
}
