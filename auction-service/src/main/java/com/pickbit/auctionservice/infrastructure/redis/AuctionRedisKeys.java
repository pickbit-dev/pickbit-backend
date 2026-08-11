package com.pickbit.auctionservice.infrastructure.redis;

/**
 * 입찰 중재에 쓰이는 Redis 키 규칙입니다.
 */
public final class AuctionRedisKeys {

    /** 진행 중 경매의 현재 상태 해시. */
    private static final String STATE_PREFIX = "auction:state:";

    /** 영속화 대기 중인 입찰 스트림. 모든 경매가 하나의 스트림을 공유해 순서를 보존한다. */
    public static final String BID_STREAM = "auction:bid:stream";

    public static final String CONSUMER_GROUP = "auction-bid-persistence";

    private AuctionRedisKeys() {
    }

    public static String state(Long auctionId) {
        return STATE_PREFIX + auctionId;
    }
}
