package com.pickbit.auctionservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 기반 입찰 중재 설정입니다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "auction.bid.arbiter")
public class BidArbiterProperties {

    /**
     * Redis 중재 사용 여부.
     *
     * <p>끄면 기존의 Redisson 분산락 + 동기 DB 트랜잭션 경로로 되돌아갑니다.
     * 중재 경로에 문제가 생겼을 때 재배포 없이 되돌릴 수 있게 남겨둔 스위치입니다.
     */
    private boolean enabled = true;

    /** 스트림 최대 길이(근사). Redis 메모리를 보호하기 위한 상한. */
    private long streamMaxLength = 100_000L;

    /** 한 번에 읽어 배치로 기록할 입찰 수. */
    private int batchSize = 200;

    /** 스트림에 새 항목이 없을 때 블로킹할 시간(ms). */
    private long blockMillis = 1_000L;

    /** 경매 종료 시 미영속 입찰이 DB에 반영되기를 기다리는 최대 시간(ms). */
    private long drainTimeoutMillis = 10_000L;
}
