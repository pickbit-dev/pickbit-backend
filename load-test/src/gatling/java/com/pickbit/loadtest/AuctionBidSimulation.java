package com.pickbit.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
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
 * 단일 경매 입찰 부하 테스트입니다. 경매 하나에 몰렸을 때의 처리량 상한을 봅니다.
 *
 * <h2>성공/실패 판정</h2>
 * 이전 버전은 {@code status().in(201, 400, 409, 429)} 로 <b>429까지 성공으로 셌고</b>
 * 실패율 단언도 없었습니다. 그래서 rate limit 에 전부 막혀도 테스트가 통과했습니다.
 *
 * <p>지금은 이렇게 나눕니다.
 * <ul>
 *   <li><b>201</b> — 성공</li>
 *   <li><b>400</b> — 경합으로 인한 정상적인 거절. 여러 사용자가 동시에 입찰하면 늦게 도착한
 *       낮은 금액이 거절되는 것은 정상이므로 실패로 세지 않는다</li>
 *   <li><b>429 / 5xx</b> — <b>실패</b>. rate limit 에 막혔거나 서버가 처리하지 못한 것이므로
 *       처리량 측정 결과를 무효로 만든다</li>
 * </ul>
 */
public class AuctionBidSimulation extends Simulation {

    private static final int RAMP_USERS = LoadTestConfig.intEnv("RAMP_USERS", 50);
    private static final int TPS = LoadTestConfig.intEnv("TPS", 100);
    private static final int RAMP_SECONDS = LoadTestConfig.intEnv("RAMP_SECONDS", 20);
    private static final int DURATION_SECONDS = LoadTestConfig.intEnv("DURATION_SECONDS", 60);

    static {
        LoadTestConfig.requireCredentials();
    }

    private final AtomicLong nextBidAmount = new AtomicLong(LoadTestConfig.BID_BASE_AMOUNT);

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            // Gatling 기본값은 가상 사용자마다 커넥션 풀을 따로 둔다. 초당 1000명을 주입하면
            // 초당 1000개의 새 TLS 커넥션이 열려 부하 발생기 쪽 ephemeral 포트가 먼저 고갈된다
            // (실제로 "Cannot assign requested address" 로 99만 건이 실패했다).
            // 커넥션을 공유해 서버가 아니라 클라이언트가 병목이 되는 것을 막는다.
            .shareConnections();

    private final ScenarioBuilder scenario = scenario("auction-bid")
            .feed(LoadTestConfig.bidderFeeder())
            .exec(http("POST bid")
                    .post("/api/auctions/" + LoadTestConfig.AUCTION_ID + "/bids")
                    .headers(LoadTestConfig.authHeaders())
                    .body(StringBody(session ->
                            "{\"bidAmount\":" + nextBidAmount.getAndAdd(LoadTestConfig.BID_INCREMENT) + "}"))
                    // 429 와 5xx 는 KO 로 잡히도록 성공 범위에서 제외한다.
                    .check(status().in(201, 400)));

    {
        setUp(
                scenario.injectOpen(
                        rampUsers(RAMP_USERS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(TPS).during(Duration.ofSeconds(DURATION_SECONDS))
                )
        ).protocols(httpProtocol)
                .assertions(
                        // rate limit / 서버 오류가 1% 를 넘으면 이 측정은 신뢰할 수 없다.
                        global().failedRequests().percent().lt(1.0),
                        details("POST bid").responseTime().percentile3().lt(1_000)
                );
    }
}
