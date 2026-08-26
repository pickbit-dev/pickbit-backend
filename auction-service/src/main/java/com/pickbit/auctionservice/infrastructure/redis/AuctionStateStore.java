package com.pickbit.auctionservice.infrastructure.redis;

import com.pickbit.auctionservice.domain.Auction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
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

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ENDED = "ENDED";

    /** Redis 에 경매 상태가 없어 순번을 발급할 수 없음을 나타냅니다. 호출자는 DB 순번으로 폴백합니다. */
    public static final long SEQUENCE_UNAVAILABLE = -1L;

    private static final RedisScript<Long> HYDRATE = RedisScript.of(
            new ClassPathResource("redis/hydrate-state.lua"), Long.class);
    private static final RedisScript<Long> NEXT_SEQUENCE = RedisScript.of(
            new ClassPathResource("redis/next-sequence.lua"), Long.class);

    private final StringRedisTemplate redis;

    /**
     * DB의 경매를 기준으로 Redis 상태를 만듭니다. <b>이미 상태가 있으면 아무것도 하지 않습니다.</b>
     *
     * <p>이 메서드는 중재 스크립트가 {@code NOT_LOADED}(키 부재)를 돌려줬을 때만 호출됩니다.
     * 그런데 동시에 여러 입찰이 {@code NOT_LOADED} 를 받으면 hydrate 도 여러 번 불립니다.
     * 예전처럼 무조건 덮어쓰면 그 사이에 반영된 입찰의 현재가와 순번이 DB 값으로 되감겨,
     * 이미 지나간 금액으로 다시 입찰할 수 있고 순번이 중복돼 누락 이벤트 복구가 어긋납니다.
     * 그래서 "없을 때만 초기화"로 원자화했습니다.
     *
     * @param knownSequence Redis 가 통째로 날아갔을 때 DB의 마지막 이벤트 순번에서 이어가기 위한 값
     * @return 새로 만들었으면 {@code true}, 이미 있어서 건드리지 않았으면 {@code false}
     */
    public boolean hydrate(Auction auction, long knownSequence) {
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

        List<String> args = new ArrayList<>(fields.size() * 2);
        fields.forEach((field, value) -> {
            args.add(field);
            args.add(value);
        });

        Long created = redis.execute(
                HYDRATE, List.of(AuctionRedisKeys.state(auction.getId())), args.toArray());

        boolean isCreated = created != null && created == 1L;
        if (isCreated) {
            log.info("경매 상태 로드 | auctionId={} | seq={}", auction.getId(), knownSequence);
        } else {
            log.debug("경매 상태가 이미 있어 로드를 건너뜁니다. auctionId={}", auction.getId());
        }
        return isCreated;
    }

    /**
     * 경매를 Redis 상에서 활성화합니다.
     *
     * <p><b>{@link #hydrate} 로는 이 전이를 반영할 수 없습니다.</b> hydrate 는 가격·순번 되감기를
     * 막으려고 "없을 때만" 쓰기 때문입니다. 그래서 이런 순서가 되면 경매가 죽습니다.
     *
     * <ol>
     *   <li>아직 {@code SCHEDULED} 인 경매에 입찰을 시도한다</li>
     *   <li>중재가 {@code NOT_LOADED} 를 돌려주고 복구 경로가 hydrate 를 부른다
     *       → Redis 에 {@code status=SCHEDULED} 로 상태가 만들어진다 (입찰은 정상 거절)</li>
     *   <li>시작 시각이 되어 스케줄러가 활성화하며 hydrate 를 부르지만
     *       키가 이미 있어 <b>무시된다</b></li>
     *   <li>DB 는 {@code ACTIVE}, Redis 는 {@code SCHEDULED} 인 채로 굳는다.
     *       이후 모든 입찰이 {@code NOT_ACTIVE} 로 거절된다 — 경매가 영구히 죽는다</li>
     * </ol>
     *
     * <p>부하 테스트에서 실제로 재현됐다. 250개 경매 전부가 DB 상 ACTIVE 인데 입찰이 100%
     * 409 로 거절됐고, Redis 에는 {@code status=SCHEDULED} 가 남아 있었다.
     *
     * <p>상태만큼은 스케줄러가 권위를 가지므로 {@link #close} 와 같이 직접 덮어씁니다.
     * 가격·순번은 건드리지 않으므로 되감기 위험은 없습니다.
     */
    public void activate(Long auctionId) {
        redis.opsForHash().put(AuctionRedisKeys.state(auctionId), FIELD_STATUS, STATUS_ACTIVE);
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

    /**
     * 입찰 이외의 이벤트(시작/종료/취소)에 붙일 순번을 발급합니다.
     *
     * <p>상태 존재 확인과 증가를 한 연산으로 처리합니다. 나눠서 보내면 그 사이에 Redis 가
     * 재시작됐을 때 없는 키에 {@code seq=1} 을 새로 만들어 DB 순번과 충돌합니다.
     *
     * @return 발급된 순번. 상태가 없으면 {@link #SEQUENCE_UNAVAILABLE}
     */
    public long nextSequence(Long auctionId) {
        Long seq = redis.execute(NEXT_SEQUENCE, List.of(AuctionRedisKeys.state(auctionId)));
        return seq == null ? SEQUENCE_UNAVAILABLE : seq;
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
