package com.pickbit.loadtest;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * 상품 목록/상세 조회 부하 테스트입니다.
 */
public class ProductReadSimulation extends Simulation {

    private static final int RAMP_USERS = LoadTestConfig.intEnv("RAMP_USERS", 50);
    private static final int RPS = LoadTestConfig.intEnv("RPS", 20);
    private static final int RAMP_SECONDS = LoadTestConfig.intEnv("RAMP_SECONDS", 30);
    private static final int DURATION_SECONDS = LoadTestConfig.intEnv("DURATION_SECONDS", 60);

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.BASE_URL)
            .acceptHeader("application/json");

    private final ScenarioBuilder scenario = scenario("product-read")
            .exec(http("GET /api/products")
                    .get("/api/products?page=0&size=20")
                    .check(status().is(200)))
            .exec(http("GET /api/products/{id}")
                    .get("/api/products/" + LoadTestConfig.PRODUCT_ID)
                    .check(status().is(200)));

    {
        setUp(
                scenario.injectOpen(
                        rampUsers(RAMP_USERS).during(Duration.ofSeconds(RAMP_SECONDS)),
                        constantUsersPerSec(RPS).during(Duration.ofSeconds(DURATION_SECONDS))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().failedRequests().percent().lt(1.0),
                        global().responseTime().percentile3().lt(500)
                );
    }
}
