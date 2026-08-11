package com.pickbit.auctionservice.infrastructure.redis;

import com.pickbit.auctionservice.domain.Auction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 진행 중 경매의 현재 상태를 Redis 에 유지합니다.
 *
 * <p>입찰 중재가 Redis 안에서 끝나야 하므로, 검증에 필요한 값(현재가, 최소 입찰 단위, 종료 시각,
 * 판매자)이 전부 여기에 올라와 있어야 합니다. Redis 가 재시작되면 키가 사라지는데,
 * 그때는 {@link #hydrate}로 DB에서 다시 만들어 넣습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionStateStore {

    private static final String FIELD_STATUS = "status";
    private static final String FIELD_CURRENT_PRICE = "currentPriceMinor";
    private static final String FIELD_MIN_INCREMENT = "minIncrementMinor";
    private static final String FIELD_STARTING_PRICE = "startingPriceMinor";
    private static final String FIELD_BUY_NOW_PRICE = "buyNowPriceMinor";
    private static final String FIELD_END_TIME = "endTimeEpochMs";
    private static final String FIELD_SELLER = "sellerUserId";
    private static final String FIELD_HAS_BID = "hasBid";
    private static final String FIELD_SEQ = "seq";
    private static final String FIELD_PERSISTED_SEQ = "persistedSeq";

    private static final String STATUS_ENDED = "ENDED";

    private final StringRedisTemplate redis;

    /**
     * DB의 경매를 기준으로 Redis 상태를 만들거나 덮어씁니다.
     *
     * <p>순번({@code seq})은 이미 발급된 값을 잃지 않도록 기존 값이 있으면 유지합니다.
     * 다만 Redis 가 통째로 날아간 경우에는 DB에 저장된 이벤트 순번에서 이어가야 하므로
     * {@code knownSequence}를 받습니다.
     */
    public void hydrate(Auction auction, long knownSequence) {
        String key = AuctionRedisKeys.state(auction.getId());

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_STATUS, auction.getAuctionStatus().name());
        fields.put(FIELD_CURRENT_PRICE, String.valueOf(MinorUnits.toMinor(auction.getCurrentPrice())));
        fields.put(FIELD_MIN_INCREMENT, String.valueOf(MinorUnits.toMinor(auction.getMinimumBidIncrement())));
        fields.put(FIELD_STARTING_PRICE, String.valueOf(MinorUnits.toMinor(auction.getStartingPrice())));
        fields.put(FIELD_BUY_NOW_PRICE, String.valueOf(MinorUnits.toMinor(auction.getBuyNowPrice())));
        fields.put(FIELD_END_TIME, String.valueOf(toEpochMs(auction)));
        fields.put(FIELD_SELLER, String.valueOf(auction.getSellerUserId()));
        // currentPrice 가 시작가보다 크면 이미 입찰이 있었다는 뜻이다.
        fields.put(FIELD_HAS_BID, hasBid(auction) ? "1" : "0");
        fields.put(FIELD_SEQ, String.valueOf(knownSequence));
        fields.put(FIELD_PERSISTED_SEQ, String.valueOf(knownSequence));

        redis.opsForHash().putAll(key, fields);
        log.info("경매 상태 로드 | auctionId={} | seq={}", auction.getId(), knownSequence);
    }

    /**
     * 경매를 Redis 상에서 종료 처리합니다. 이후 입찰은 Lua 스크립트에서 NOT_ACTIVE 로 거절됩니다.
     */
    public void close(Long auctionId) {
        redis.opsForHash().put(AuctionRedisKeys.state(auctionId), FIELD_STATUS, STATUS_ENDED);
    }

    public void remove(Long auctionId) {
        redis.delete(AuctionRedisKeys.state(auctionId));
    }

    /** 입찰 이외의 이벤트(시작/종료/취소)에 붙일 순번을 발급합니다. */
    public long nextSequence(Long auctionId) {
        Long seq = redis.opsForHash().increment(AuctionRedisKeys.state(auctionId), FIELD_SEQ, 1L);
        return seq == null ? 0L : seq;
    }

    /** 영속화 워커가 DB 반영을 마친 순번을 기록합니다. */
    public void markPersisted(Long auctionId, long sequence) {
        redis.opsForHash().put(AuctionRedisKeys.state(auctionId), FIELD_PERSISTED_SEQ, String.valueOf(sequence));
    }

    /**
     * 현재 상태를 읽습니다. 키가 없으면 {@code null} 을 반환합니다.
     */
    public AuctionState read(Long auctionId) {
        List<Object> values = redis.opsForHash().multiGet(
                AuctionRedisKeys.state(auctionId),
                List.of(FIELD_STATUS, FIELD_CURRENT_PRICE, FIELD_HAS_BID, FIELD_SEQ, FIELD_PERSISTED_SEQ));

        if (values.isEmpty() || values.getFirst() == null) {
            return null;
        }
        return new AuctionState(
                String.valueOf(values.get(0)),
                MinorUnits.toAmount(parseLong(values.get(1))),
                "1".equals(String.valueOf(values.get(2))),
                parseLong(values.get(3)),
                parseLong(values.get(4)));
    }

    private static boolean hasBid(Auction auction) {
        return auction.getCurrentPrice() != null
                && auction.getStartingPrice() != null
                && auction.getCurrentPrice().compareTo(auction.getStartingPrice()) > 0;
    }

    private static long toEpochMs(Auction auction) {
        if (auction.getEndTime() == null) {
            return Long.MAX_VALUE;
        }
        return auction.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 테스트/진단용. 현재가를 BigDecimal 로 바로 얻는다. */
    public BigDecimal currentPrice(Long auctionId) {
        AuctionState state = read(auctionId);
        return state == null ? null : state.currentPrice();
    }
}
