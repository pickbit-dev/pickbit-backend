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
 * 합산 처리량 목표(기본 1000 rps)를 재는 혼합 부하 테스트입니다.
 *
 * <p>세 종류의 트래픽을 실제 서비스에 가까운 비율로 섞습니다.
 *
 * <table>
 *   <caption>트래픽 구성</caption>
 *   <tr><th>시나리오</th><th>비율</th><th>특징</th></tr>
 *   <tr><td>공개 조회</td><td>80%</td><td>비로그인. 게이트웨이 인증을 건너뛰고 캐시에서 응답</td></tr>
 *   <tr><td>인증 조회</td><td>15%</td><td>JWT 또는 API key 검증 + 본인 자원 조회</td></tr>
 *   <tr><td>입찰</td><td>5%</td><td>쓰기. Redis 중재 경로</td></tr>
 * </table>
 *
 * <p>구간별로 응답 시간을 따로 단언하므로 어느 경로가 느린지 바로 드러납니다.
 */
public class MixedLoadSimulation extends Simulation {

    /** 목표 합산 처리량. */
    private static final int TOTAL_RPS = LoadTestConfig.intEnv("TOTAL_RPS", 1_000);
    private static final int RAMP_SECONDS = LoadTestConfig.intEnv("RAMP_SECONDS", 60);
    private static final int DURATION_SECONDS = LoadTestConfig.intEnv("DURATION_SECONDS", 180);

    private static final int PUBLIC_READ_RPS = TOTAL_RPS * 80 / 100;
    private static final int AUTH_READ_RPS = TOTAL_RPS * 15 / 100;
    private static final int BID_RPS = TOTAL_RPS * 5 / 100;

    static {
        LoadTestConfig.requireCredentials();
    }

    private final Map<Long, AtomicLong> nextAmountByAuction = new ConcurrentHashMap<>();

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            // Gatling 기본값은 가상 사용자마다 커넥션 풀을 따로 둔다. 초당 1000명을 주입하면
            // 초당 1000개의 새 TLS 커넥션이 열려 부하 발생기 쪽 ephemeral 포트가 먼저 고갈된다
            // (실제로 "Cannot assign requested address" 로 99만 건이 실패했다).
            // 커넥션을 공유해 서버가 아니라 클라이언트가 병목이 되는 것을 막는다.
            .shareConnections();

    // 80% — 비로그인 카탈로그 조회. 게이트웨이 인증을 타지 않는다.
    private final ScenarioBuilder publicRead = scenario("public-read")
            .exec(http("GET products").get("/api/products?page=0&size=20").check(status().is(200)))
            .exec(http("GET product detail")
                    .get("/api/products/" + LoadTestConfig.PRODUCT_ID).check(status().is(200)))
            .exec(http("GET auctions").get("/api/auctions?page=0&size=20").check(status().is(200)));

    // 15% — 인증이 필요한 본인 자원 조회.
    private final ScenarioBuilder authenticatedRead = scenario("authenticated-read")
            .feed(LoadTestConfig.bidderFeeder())
            .exec(http("GET my selling")
                    .get("/api/products/me/selling?page=0&size=20")
                    .headers(LoadTestConfig.authHeaders())
                    .check(status().is(200)))
            .exec(http("GET my payments")
                    .get("/api/payments/me?page=0&size=20")
                    .headers(LoadTestConfig.authHeaders())
                    .check(status().is(200)));

    // 5% — 쓰기. 여러 경매에 분산한다.
    private final ScenarioBuilder bid = scenario("bid")
            .feed(LoadTestConfig.bidderFeeder())
            .exec(session -> session.set("auctionId",
                    LoadTestConfig.AUCTION_ID_BASE
                            + (ThreadLocalRandom.current().nextInt(LoadTestConfig.AUCTION_COUNT))))
            .exec(http("POST bid")
                    .post("/api/auctions/#{auctionId}/bids")
                    .headers(LoadTestConfig.authHeaders())
                    .body(StringBody(session -> {
                        long auctionId = session.getLong("auctionId");
                        long amount = nextAmountByAuction
                                .computeIfAbsent(auctionId, id -> new AtomicLong(LoadTestConfig.BID_BASE_AMOUNT))
                                .getAndAdd(LoadTestConfig.BID_INCREMENT);
                        return "{\"bidAmount\":" + amount + "}";
                    }))
                    // 거절은 정상 동작이다: 409 = 최저가 미달·경합 패배, 403 = 자기 경매.
                    // 429(rate limit)와 5xx 만 실패로 본다.
                    .check(status().in(201, 400, 403, 409)));

    {
        setUp(
                publicRead.injectOpen(
                        rampUsers(PUBLIC_READ_RPS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(PUBLIC_READ_RPS).during(Duration.ofSeconds(DURATION_SECONDS))),
                authenticatedRead.injectOpen(
                        rampUsers(AUTH_READ_RPS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(AUTH_READ_RPS).during(Duration.ofSeconds(DURATION_SECONDS))),
                bid.injectOpen(
                        rampUsers(BID_RPS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(BID_RPS).during(Duration.ofSeconds(DURATION_SECONDS)))
        ).protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        // 공개 조회는 캐시에서 나가야 하므로 가장 빨라야 한다.
                        details("GET products").responseTime().percentile3().lt(200),
                        details("GET product detail").responseTime().percentile3().lt(200),
                        details("GET auctions").responseTime().percentile3().lt(200),
                        // 인증 경로. 게이트웨이가 JWT 를 자체 검증하므로 auth-service 왕복이 없다.
                        details("GET my selling").responseTime().percentile3().lt(300),
                        details("GET my payments").responseTime().percentile3().lt(300),
                        details("POST bid").responseTime().percentile3().lt(500)
                );
    }
}
