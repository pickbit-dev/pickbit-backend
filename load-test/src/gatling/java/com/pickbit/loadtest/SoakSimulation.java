package com.pickbit.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * 장시간 soak 용 혼합 시나리오입니다.
 *
 * <p>{@link MixedLoadSimulation} 은 조회 95% / 입찰 5% 라 사실상 읽기만 때린다.
 * 실제 서비스는 등록·경매개시 같은 쓰기가 섞이고, 그 쓰기가 아웃박스 → Kafka → CDC 까지
 * 파급되므로 읽기만으로는 파이프라인 전체를 검증하지 못한다. 그래서 쓰기 비중을 올렸다.
 *
 * <p>판매 흐름은 한 가상 사용자 안에서 <b>상품 등록 → 그 상품으로 경매 개시</b>로 이어진다.
 * 실제 사용자 동선이기도 하고, 미리 만들어 둔 상품 풀에 의존하지 않아 오래 돌려도 마르지 않는다.
 */
public class SoakSimulation extends Simulation {

    private static final int TOTAL_RPS = LoadTestConfig.intEnv("TOTAL_RPS", 400);
    private static final int RAMP_SECONDS = LoadTestConfig.intEnv("RAMP_SECONDS", 120);
    private static final int DURATION_SECONDS = LoadTestConfig.intEnv("DURATION_SECONDS", 3600);

    // 쓰기는 읽기보다 무겁고 데이터도 쌓이므로 비중을 낮게 잡되, 없지는 않게 둔다.
    private static final int READ_RPS = Math.max(1, TOTAL_RPS * 55 / 100);
    private static final int BID_RPS = Math.max(1, TOTAL_RPS * 30 / 100);
    private static final int SELL_RPS = Math.max(1, TOTAL_RPS * 10 / 100);
    private static final int MYPAGE_RPS = Math.max(1, TOTAL_RPS * 3 / 100);
    /** 결제는 낙찰 수에 묶여 있어 많이 못 돈다. 낮게 두고 경로가 도는지 확인하는 용도다. */
    private static final int PAY_RPS = Math.max(1, TOTAL_RPS * 2 / 100);

    /** 상품 이미지. 매번 올리면 오브젝트 스토리지 비용만 늘어 이미 올라간 것을 재사용한다. */
    private static final String IMAGE_URL = LoadTestConfig.env("SEED_IMAGE_URL",
            "https://kr.object.ncloudstorage.com/pickbit-server-develop-storage/files/"
                    + "b51f9597-667a-4eda-9e73-1fbb149334cf.jpg");

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<Long, AtomicLong> nextAmountByAuction = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            // 가상 사용자마다 커넥션 풀을 따로 두면 초당 수백 개의 커넥션이 열려
            // 부하 발생기 쪽 포트가 먼저 고갈된다.
            .shareConnections();

    // 55% — 카탈로그 조회
    private final ScenarioBuilder read = scenario("read")
            .exec(http("GET products").get("/api/products?page=0&size=20").check(status().is(200)))
            .exec(http("GET auctions").get("/api/auctions?page=0&size=20").check(status().is(200)));

    // 30% — 입찰
    private final ScenarioBuilder bid = scenario("bid")
            .feed(LoadTestConfig.bidderFeeder())
            .exec(session -> session.set("auctionId", LoadTestConfig.AUCTION_ID_BASE
                    + ThreadLocalRandom.current().nextInt(LoadTestConfig.AUCTION_COUNT)))
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
                    // 409 = 최저가 미달·경합 패배, 403 = 자기 경매. 둘 다 정상 거절이다.
                    .check(status().in(201, 400, 403, 409)));

    // 10% — 판매 흐름: 상품 등록 -> 그 상품으로 경매 개시
    private final ScenarioBuilder sell = scenario("sell")
            .feed(LoadTestConfig.bidderFeeder())
            .exec(http("POST product")
                    .post("/api/products")
                    .headers(LoadTestConfig.authHeaders())
                    .body(StringBody(session -> {
                        long n = seq.getAndIncrement();
                        return "{\"name\":\"부하테스트 상품 " + n + "\","
                                + "\"description\":\"부하 테스트로 생성된 상품입니다. 실제 매물이 아닙니다.\","
                                + "\"startingPrice\":50000,"
                                + "\"productCondition\":\"GOOD\","
                                + "\"categoryId\":" + (1 + (n % 20)) + ","
                                + "\"images\":[{\"imageUrl\":\"" + IMAGE_URL + "\","
                                + "\"imageType\":\"THUMBNAIL\",\"sortOrder\":0}]}";
                    }))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("newProductId")))
            .exec(http("POST auction")
                    .post("/api/auctions")
                    .headers(LoadTestConfig.authHeaders())
                    .body(StringBody(session -> {
                        // startTime 은 @Future 제약이 있다. 시계 오차를 감안해 넉넉히 뒤로 둔다.
                        LocalDateTime now = LocalDateTime.now();
                        return "{\"productId\":" + session.getString("newProductId") + ","
                                + "\"startingPrice\":50000,"
                                + "\"minimumBidIncrement\":100,"
                                + "\"startTime\":\"" + now.plusMinutes(2).format(TS) + "\","
                                + "\"endTime\":\"" + now.plusHours(6).format(TS) + "\"}";
                    }))
                    .check(status().in(201, 400, 409)));

    // 5% — 마이페이지 (인증 조회 + 결제 목록)
    private final ScenarioBuilder mypage = scenario("mypage")
            .feed(LoadTestConfig.bidderFeeder())
            .exec(http("GET my selling")
                    .get("/api/products/me/selling?page=0&size=20")
                    .headers(LoadTestConfig.authHeaders()).check(status().is(200)))
            .exec(http("GET my payments")
                    .get("/api/payments/me?page=0&size=20")
                    .headers(LoadTestConfig.authHeaders()).check(status().is(200)));

    // 2% — 결제: 내 결제 목록 -> 대기중인 게 있으면 요청정보 조회 후 결제 전 취소
    //
    // 토스 confirm 은 넣을 수 없다. paymentKey 가 결제 위젯에서만 발급되기 때문이다.
    // 대신 낙찰로 생긴 REQUESTED 결제를 조회하고 취소까지 태운다. 취소는 PG 를 호출하지 않는
    // 순수 DB 경로라 부하로 돌려도 외부 결제사에 영향이 없다.
    private final ScenarioBuilder pay = scenario("pay")
            .feed(LoadTestConfig.bidderFeeder())
            .exec(http("GET payments to settle")
                    .get("/api/payments/me?page=0&size=5")
                    .headers(LoadTestConfig.authHeaders())
                    .check(status().is(200))
                    .check(jsonPath("$.content[0].paymentId").optional().saveAs("payId")))
            .doIf(session -> session.contains("payId")).then(
                    exec(http("GET payment request-info")
                            .get("/api/payments/#{payId}/request-info")
                            .headers(LoadTestConfig.authHeaders())
                            .check(status().in(200, 403, 404, 409)))
                            .exec(http("POST cancel before pay")
                                    .post("/api/payments/#{payId}/cancel-before-pay")
                                    .headers(LoadTestConfig.authHeaders())
                                    // 이미 결제·취소됐으면 거절이 정상이다.
                                    .check(status().in(200, 204, 400, 403, 404, 409))));

    {
        LoadTestConfig.requireCredentials();
        setUp(
                read.injectOpen(
                        rampUsers(READ_RPS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(READ_RPS).during(Duration.ofSeconds(DURATION_SECONDS))),
                bid.injectOpen(
                        rampUsers(BID_RPS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(BID_RPS).during(Duration.ofSeconds(DURATION_SECONDS))),
                sell.injectOpen(
                        rampUsers(SELL_RPS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(SELL_RPS).during(Duration.ofSeconds(DURATION_SECONDS))),
                mypage.injectOpen(
                        rampUsers(MYPAGE_RPS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(MYPAGE_RPS).during(Duration.ofSeconds(DURATION_SECONDS))),
                pay.injectOpen(
                        rampUsers(PAY_RPS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(PAY_RPS).during(Duration.ofSeconds(DURATION_SECONDS))))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().percent().lt(1.0));
    }
}
