package com.pickbit.loadtest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gatling 시뮬레이션 공통 설정입니다.
 *
 * <p>인증은 게이트웨이의 테스트용 API key 를 씁니다. 예전에는 사용자별 JWT 를 미리 발급해
 * {@code bidders.csv} 에 넣어뒀는데, 토큰이 만료되면 전체 테스트가 무용지물이 됐고
 * 인원을 늘리려면 그만큼 토큰을 다시 발급해야 했습니다. API key 는 사용자 ID 를 헤더로
 * 지정하므로 인원을 숫자만 바꿔서 늘릴 수 있습니다.
 *
 * <pre>
 * # 게이트웨이에서 API key 를 켜고 (document/operations/api-key-testing.md 참고)
 * export API_KEY=$(cat ...)
 * export BASE_URL=https://api.pickbit.co.kr
 * ./gradlew :load-test:gatlingRun-com.pickbit.loadtest.MixedLoadSimulation
 * </pre>
 */
public final class LoadTestConfig {

    public static final String BASE_URL = env("BASE_URL", "http://localhost:18080");

    /** 게이트웨이 테스트용 API key. 없으면 ACCESS_TOKEN 으로 폴백한다. */
    public static final String API_KEY = env("API_KEY", "");

    /** API key 를 쓰지 않을 때 사용할 단일 사용자 토큰. */
    public static final String ACCESS_TOKEN = env("ACCESS_TOKEN", "");

    public static final String PRODUCT_ID = env("PRODUCT_ID", "1");
    public static final String AUCTION_ID = env("AUCTION_ID", "1");

    /** 다중 경매 시나리오에서 사용할 경매 ID 범위 시작값. */
    public static final long AUCTION_ID_BASE = longEnv("AUCTION_ID_BASE", 1L);
    public static final int AUCTION_COUNT = intEnv("AUCTION_COUNT", 20);

    /** 서로 다른 입찰자 수. rate limit(사용자당 10/s)을 넘지 않으려면 목표 TPS / 10 이상이어야 한다. */
    public static final int BIDDER_COUNT = intEnv("BIDDER_COUNT", 500);

    /** 입찰자 사용자 ID 범위 시작값. */
    public static final long BIDDER_ID_BASE = longEnv("BIDDER_ID_BASE", 1_000L);

    public static final long BID_BASE_AMOUNT = longEnv("BID_BASE_AMOUNT", 10_000L);
    public static final long BID_INCREMENT = longEnv("BID_INCREMENT", 1_000L);

    private LoadTestConfig() {
    }

    public static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static int intEnv(String key, int defaultValue) {
        return Integer.parseInt(env(key, String.valueOf(defaultValue)));
    }

    public static long longEnv(String key, long defaultValue) {
        return Long.parseLong(env(key, String.valueOf(defaultValue)));
    }

    public static boolean usingApiKey() {
        return !API_KEY.isBlank();
    }

    /**
     * 특정 사용자로 요청할 때 붙일 헤더입니다.
     *
     * <p>Gatling 세션 변수 {@code #{userId}} 를 그대로 넘기므로, 피더가 사용자마다 다른 값을
     * 채워주면 요청마다 다른 사용자로 인증됩니다.
     */
    public static Map<String, String> authHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (usingApiKey()) {
            headers.put("X-Api-Key", API_KEY);
            headers.put("X-Api-User-Id", "#{userId}");
            headers.put("X-Api-Nickname", "loadtest-#{userId}");
        } else {
            requireAccessToken();
            headers.put("Authorization", "Bearer " + ACCESS_TOKEN);
        }
        return headers;
    }

    public static void requireCredentials() {
        if (!usingApiKey() && ACCESS_TOKEN.isBlank()) {
            throw new IllegalStateException(
                    "API_KEY 또는 ACCESS_TOKEN 환경변수가 필요합니다. "
                            + "API key 사용법은 document/operations/api-key-testing.md 참고.");
        }
    }

    public static void requireAccessToken() {
        if (ACCESS_TOKEN.isBlank()) {
            throw new IllegalStateException("ACCESS_TOKEN environment variable is required for this simulation.");
        }
    }

    /**
     * 사용자 ID 를 순환 공급하는 피더입니다.
     *
     * <p>CSV 파일 대신 생성해서 씁니다. 인원을 500명에서 5000명으로 늘리는 데 환경변수 하나면 됩니다.
     *
     * <p>Gatling 은 여러 가상 사용자 스레드에서 동시에 피더를 당기므로 반드시 스레드 안전해야
     * 합니다. {@code Stream.iterate(...).iterator()} 는 그렇지 않아 쓰지 않습니다.
     */
    public static Iterator<Map<String, Object>> bidderFeeder() {
        AtomicLong cursor = new AtomicLong();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Map<String, Object> next() {
                long index = cursor.getAndIncrement() % BIDDER_COUNT;
                Map<String, Object> session = new HashMap<>();
                session.put("userId", BIDDER_ID_BASE + index);
                return session;
            }
        };
    }
}
