package com.pickbit.productservice.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 상품 조회수를 Redis 에 모았다가 주기적으로 DB에 반영합니다.
 *
 * <p>예전에는 상품 상세를 조회할 때마다 {@code UPDATE product SET view_count = view_count + 1}
 * 이 실행됐습니다. 조회 API가 사실상 쓰기 API였던 셈이라 캐싱을 해도 DB 부하가 그대로였고,
 * 같은 상품에 조회가 몰리면 같은 행에 UPDATE 가 몰려 락 경합까지 생겼습니다.
 *
 * <p>지금은 Redis 해시에 {@code HINCRBY} 로 모으고 스케줄러가 한 번에 반영합니다.
 * 조회수는 정확할 필요가 없는 값이라 이 정도 지연은 문제가 되지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountBuffer {

    private static final String KEY = "product:viewcount:pending";

    private final StringRedisTemplate redis;

    public void increase(Long productId) {
        try {
            redis.opsForHash().increment(KEY, String.valueOf(productId), 1L);
        } catch (RuntimeException e) {
            // 조회수는 부가 정보다. Redis 가 흔들려도 상품 조회 자체는 성공해야 한다.
            log.warn("조회수 집계에 실패했습니다. productId={}", productId, e);
        }
    }

    /**
     * 모아둔 조회수를 꺼내고 버퍼를 비웁니다.
     *
     * <p>읽기와 삭제 사이에 들어온 조회는 다음 주기로 넘어갑니다. 조회수 특성상 허용 가능한
     * 오차라 굳이 원자적으로 처리하지 않습니다.
     */
    public Map<Long, Long> drain() {
        Map<Object, Object> pending = redis.opsForHash().entries(KEY);
        if (pending.isEmpty()) {
            return Map.of();
        }

        Set<Object> fields = pending.keySet();
        Map<Long, Long> counts = new HashMap<>(pending.size());
        for (Map.Entry<Object, Object> entry : pending.entrySet()) {
            try {
                counts.put(Long.valueOf(String.valueOf(entry.getKey())),
                        Long.valueOf(String.valueOf(entry.getValue())));
            } catch (NumberFormatException e) {
                log.warn("조회수 버퍼에 잘못된 값이 있어 건너뜁니다. {}={}", entry.getKey(), entry.getValue());
            }
        }
        redis.opsForHash().delete(KEY, fields.toArray());
        return counts;
    }

    /** 진단용. 아직 반영되지 않은 조회수. */
    public Long pending(Long productId) {
        Object value = redis.opsForHash().get(KEY, String.valueOf(productId));
        return value == null ? 0L : Long.valueOf(String.valueOf(value));
    }

    /** 진단용. 버퍼에 쌓인 상품 수. */
    public long pendingProductCount() {
        Long size = redis.opsForHash().size(KEY);
        return size == null ? 0L : size;
    }

    static List<String> keys() {
        return List.of(KEY);
    }
}
