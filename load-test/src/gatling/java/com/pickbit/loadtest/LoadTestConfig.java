package com.pickbit.loadtest;

import java.util.Map;

/**
 * Gatling 시뮬레이션 공통 설정입니다.
 */
public final class LoadTestConfig {

    public static final String BASE_URL = env("BASE_URL", "http://localhost:18080");
    public static final String ACCESS_TOKEN = env("ACCESS_TOKEN", "");
    public static final String PRODUCT_ID = env("PRODUCT_ID", "1");
    public static final String AUCTION_ID = env("AUCTION_ID", "1");
    public static final long BID_BASE_AMOUNT = longEnv("BID_BASE_AMOUNT", 10_000L);
    public static final long BID_INCREMENT = longEnv("BID_INCREMENT", 1_000L);

    private LoadTestConfig() {
    }

    public static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static int intEnv(String key, int defaultValue) {
        String value = env(key, String.valueOf(defaultValue));
        return Integer.parseInt(value);
    }

    public static long longEnv(String key, long defaultValue) {
        String value = env(key, String.valueOf(defaultValue));
        return Long.parseLong(value);
    }

    public static Map<String, String> authHeaders() {
        requireAccessToken();
        return Map.of(
                "Authorization", "Bearer " + ACCESS_TOKEN,
                "Content-Type", "application/json"
        );
    }

    public static void requireAccessToken() {
        if (ACCESS_TOKEN.isBlank()) {
            throw new IllegalStateException("ACCESS_TOKEN environment variable is required for this simulation.");
        }
    }
}
