package com.pickbit.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * 여러 경매에 분산된 입찰 부하 테스트입니다.
 *
 * <p>입찰 중재는 경매 단위로 이뤄지므로 서로 다른 경매의 입찰은 병렬로 처리됩니다.
 * 단일 경매 시나리오({@link AuctionBidSimulation})와 이 시나리오의 처리량을 비교하면
 * "경매 하나의 상한"과 "시스템 전체 상한"을 분리해서 볼 수 있습니다.
 *
 * <p>개선 전(Redisson 락을 DB 트랜잭션 전체 동안 보유)에는 경매 하나당 상한이 낮아
 * 경매를 늘려야만 전체 처리량이 올라갔습니다. 개선 후에는 단일 경매 처리량 자체가 올라가야
 * 합니다. 두 수치를 같이 기록하세요.
 */
public class MultiAuctionBidSimulation extends Simulation {

    private static final int RAMP_USERS = LoadTestConfig.intEnv("RAMP_USERS", 100);
    private static final int TPS = LoadTestConfig.intEnv("TPS", 500);
    private static final int RAMP_SECONDS = LoadTestConfig.intEnv("RAMP_SECONDS", 30);
    private static final int DURATION_SECONDS = LoadTestConfig.intEnv("DURATION_SECONDS", 60);

    static {
        LoadTestConfig.requireCredentials();
    }

    /** 경매마다 입찰가가 독립적으로 올라가야 하므로 카운터도 경매별로 둔다. */
    private final Map<Long, AtomicLong> nextAmountByAuction = new ConcurrentHashMap<>();

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private long nextAuctionId() {
        return LoadTestConfig.AUCTION_ID_BASE
                + (ThreadLocalRandom.current().nextInt(LoadTestConfig.AUCTION_COUNT));
    }

    private long nextAmount(long auctionId) {
        return nextAmountByAuction
                .computeIfAbsent(auctionId, id -> new AtomicLong(LoadTestConfig.BID_BASE_AMOUNT))
                .getAndAdd(LoadTestConfig.BID_INCREMENT);
    }

    private final ScenarioBuilder scenario = scenario("multi-auction-bid")
            .feed(LoadTestConfig.bidderFeeder())
            // 경매 ID 를 세션에 담아 URL 과 금액이 같은 경매를 가리키게 한다.
            .exec(session -> session.set("auctionId", nextAuctionId()))
            .exec(http("POST bid (multi-auction)")
                    .post("/api/auctions/#{auctionId}/bids")
                    .headers(LoadTestConfig.authHeaders())
                    .body(StringBody(session ->
                            "{\"bidAmount\":" + nextAmount(session.getLong("auctionId")) + "}"))
                    .check(status().in(201, 400, 403, 409)));

    {
        setUp(
                scenario.injectOpen(
                        rampUsers(RAMP_USERS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(TPS).during(Duration.ofSeconds(DURATION_SECONDS))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        details("POST bid (multi-auction)").responseTime().percentile3().lt(1_000)
                );
    }
}
