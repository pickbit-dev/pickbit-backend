package com.pickbit.loadtest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gatling 시뮬레이션 공통 설정입니다.
 *
 * <p>인증 방식은 세 가지이고 우선순위대로 고릅니다.
 *
 * <ol>
 *   <li>{@code TOKENS_FILE} — 사용자마다 실제 로그인해 받은 JWT 목록. 운영 부하 테스트는
 *       이걸 쓴다. API key 와 달리 인증 우회가 아니다.</li>
 *   <li>{@code API_KEY} — 게이트웨이 테스트용 키. 사용자 ID 를 헤더로 지정한다.
 *       인증 우회이므로 운영에서는 켜지 않는 편이 좋다.</li>
 *   <li>{@code ACCESS_TOKEN} — 단일 사용자 토큰. 모든 요청이 한 사용자로 나가므로
 *       rate limit(사용자당 10/s)에 걸린다. 가벼운 확인용으로만 쓴다.</li>
 * </ol>
 *
 * <p>예전에는 {@code bidders.csv} 에 토큰을 손으로 넣어뒀는데 만료되면 테스트가 통째로
 * 무용지물이 됐다(실제로 2026-06-18 만료). 지금은 발급 스크립트가 파일을 만들고,
 * 테스트 시간 동안만 {@code JWT_ACCESS_TOKEN_VALIDITY_MS} 를 늘려 만료를 피한다.
 *
 * <pre>
 * export BASE_URL=https://api.pickbit.co.kr
 * export TOKENS_FILE=/path/to/tokens.csv   # userId,accessToken
 * ./gradlew :load-test:gatlingRun-com.pickbit.loadtest.MixedLoadSimulation
 * </pre>
 */
public final class LoadTestConfig {

    public static final String BASE_URL = env("BASE_URL", "http://localhost:18080");

    /** 게이트웨이 테스트용 API key. 없으면 ACCESS_TOKEN 으로 폴백한다. */
    public static final String API_KEY = env("API_KEY", "");

    /** API key 를 쓰지 않을 때 사용할 단일 사용자 토큰. */
    public static final String ACCESS_TOKEN = env("ACCESS_TOKEN", "");
    /**
     * 사용자별 JWT 목록 파일. 한 줄에 {@code userId,accessToken} 형식이다.
     *
     * <p>API key 는 인증 우회라 운영에서 켜기 부담스럽다. 그렇다고 {@code ACCESS_TOKEN} 하나로
     * 돌리면 모든 요청이 같은 사용자로 나가 게이트웨이 rate limit(사용자당 10/s)에 걸려
     * 전체 처리량이 10 rps 로 막힌다. 그래서 사용자마다 실제로 로그인해 받은 토큰을
     * 파일로 넘겨 요청마다 다른 사용자로 인증한다.
     *
     * <p>토큰 만료(기본 30분)보다 테스트가 길면 중간에 전부 401 이 되므로, 테스트 시간 동안만
     * {@code JWT_ACCESS_TOKEN_VALIDITY_MS} 를 늘려두고 끝나면 되돌린다.
     */
    public static final String TOKENS_FILE = env("TOKENS_FILE", "");

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

    public static boolean usingTokenFile() {
        return !TOKENS_FILE.isBlank();
    }

    /**
     * {@code TOKENS_FILE} 을 읽어 {@code userId -> token} 목록으로 만듭니다.
     *
     * <p>클래스 로딩 시 한 번만 읽습니다. 파일이 없거나 비면 즉시 예외를 던집니다 —
     * 토큰 없이 조용히 시작해서 전 요청이 401 로 실패하는 것보다 낫습니다.
     */
    private static List<Map<String, Object>> loadTokens() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(Path.of(TOKENS_FILE), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int comma = trimmed.indexOf(',');
                if (comma <= 0) {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("userId", Long.parseLong(trimmed.substring(0, comma).trim()));
                row.put("token", trimmed.substring(comma + 1).trim());
                rows.add(row);
            }
        } catch (IOException e) {
            throw new IllegalStateException("TOKENS_FILE 을 읽을 수 없습니다: " + TOKENS_FILE, e);
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("TOKENS_FILE 에 사용할 토큰이 없습니다: " + TOKENS_FILE);
        }
        return rows;
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
        } else if (usingTokenFile()) {
            // 피더가 사용자마다 다른 토큰을 채운다. 요청마다 실제로 다른 사용자로 인증되므로
            // rate limit 이 사용자 단위로 흩어진다.
            headers.put("Authorization", "Bearer #{token}");
        } else {
            requireAccessToken();
            headers.put("Authorization", "Bearer " + ACCESS_TOKEN);
        }
        return headers;
    }

    public static void requireCredentials() {
        if (!usingApiKey() && !usingTokenFile() && ACCESS_TOKEN.isBlank()) {
            throw new IllegalStateException(
                    "API_KEY, TOKENS_FILE, ACCESS_TOKEN 중 하나가 필요합니다. "
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
        // TOKENS_FILE 을 쓰면 사용자 수는 파일이 정한다. BIDDER_COUNT/BIDDER_ID_BASE 로
        // 만들어낸 가상의 ID 를 쓰면 실제로 존재하지 않는 사용자가 되어 전부 실패한다.
        List<Map<String, Object>> tokens = usingTokenFile() ? loadTokens() : null;
        int size = tokens != null ? tokens.size() : BIDDER_COUNT;
        AtomicLong cursor = new AtomicLong();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Map<String, Object> next() {
                int index = (int) (cursor.getAndIncrement() % size);
                if (tokens != null) {
                    // 피더는 여러 스레드에서 동시에 당겨진다. 원본 맵을 그대로 넘기면
                    // 세션이 공유돼 서로 덮어쓸 수 있으므로 복사해서 넘긴다.
                    return new HashMap<>(tokens.get(index));
                }
                Map<String, Object> session = new HashMap<>();
                session.put("userId", BIDDER_ID_BASE + index);
                return session;
            }
        };
    }
}
