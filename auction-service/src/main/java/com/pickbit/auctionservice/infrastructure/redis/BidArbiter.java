package com.pickbit.auctionservice.infrastructure.redis;

import com.pickbit.auctionservice.config.BidArbiterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 입찰 검증과 현재가 갱신을 Redis 안에서 원자적으로 처리합니다.
 *
 * <p>왕복 한 번으로 끝나므로 한 경매의 입찰이 DB 트랜잭션 길이만큼 직렬화되던 제약이 사라집니다.
 * 실제 DB 기록은 {@code auction:bid:stream} 을 통해 비동기로 이어집니다.
 */
@Component
@RequiredArgsConstructor
public class BidArbiter {

    private static final RedisScript<List> PLACE_BID = RedisScript.of(
            new ClassPathResource("redis/place-bid.lua"), List.class);

    private final StringRedisTemplate redis;
    private final BidArbiterProperties properties;

    public BidArbitrationResult placeBid(
            Long auctionId,
            Long bidderUserId,
            String bidderNickname,
            BigDecimal amount,
            long nowEpochMs) {

        List<?> raw = redis.execute(
                PLACE_BID,
                List.of(AuctionRedisKeys.state(auctionId), AuctionRedisKeys.BID_STREAM),
                String.valueOf(bidderUserId),
                bidderNickname,
                String.valueOf(MinorUnits.toMinor(amount)),
                String.valueOf(nowEpochMs),
                String.valueOf(properties.getStreamMaxLength()),
                String.valueOf(auctionId));

        if (raw == null || raw.size() < 6) {
            throw new IllegalStateException("입찰 중재 스크립트 응답이 올바르지 않습니다: " + raw);
        }

        return new BidArbitrationResult(
                "1".equals(str(raw.get(0))),
                str(raw.get(1)),
                MinorUnits.toAmount(Long.parseLong(str(raw.get(2)))),
                Long.parseLong(str(raw.get(3))),
                "1".equals(str(raw.get(4))),
                Long.parseLong(str(raw.get(5))));
    }

    private static String str(Object value) {
        return value instanceof byte[] bytes ? new String(bytes) : String.valueOf(value);
    }
}
