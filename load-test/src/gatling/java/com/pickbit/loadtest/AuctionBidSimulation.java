package com.pickbit.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * 단일 경매 입찰 부하 테스트입니다.
 */
public class AuctionBidSimulation extends Simulation {

    private static final int RAMP_USERS = LoadTestConfig.intEnv("RAMP_USERS", 10);
    private static final int TPS = LoadTestConfig.intEnv("TPS", 5);
    private static final int RAMP_SECONDS = LoadTestConfig.intEnv("RAMP_SECONDS", 20);
    private static final int DURATION_SECONDS = LoadTestConfig.intEnv("DURATION_SECONDS", 30);

    private final AtomicLong nextBidAmount = new AtomicLong(LoadTestConfig.BID_BASE_AMOUNT);

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder scenario = scenario("auction-bid")
            .feed(csv("bidders.csv").circular())
            .exec(http("POST /api/auctions/{auctionId}/bids")
                    .post("/api/auctions/" + LoadTestConfig.AUCTION_ID + "/bids")
                    .header("Authorization", "Bearer #{accessToken}")
                    .body(StringBody(session -> {
                        long amount = nextBidAmount.getAndAdd(LoadTestConfig.BID_INCREMENT);
                        return "{\"bidAmount\":" + amount + "}";
                    }))
                    .check(status().in(201, 400, 409, 429)));

    {
        setUp(
                scenario.injectOpen(
                        rampUsers(RAMP_USERS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(TPS).during(Duration.ofSeconds(DURATION_SECONDS))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile3().lt(1_000)
                );
    }
}
