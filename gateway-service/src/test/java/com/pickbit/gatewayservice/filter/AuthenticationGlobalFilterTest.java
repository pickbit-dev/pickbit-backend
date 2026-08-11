package com.pickbit.gatewayservice.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import com.pickbit.gatewayservice.security.ApiKeyAuthenticator;
import com.pickbit.gatewayservice.security.ApiKeyProperties;
import com.pickbit.gatewayservice.security.GatewayJwtDecoder;
import com.pickbit.gatewayservice.security.RoleAuthorizationRules;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게이트웨이 인증 필터 검증.
 * 토큰 검증이 게이트웨이 안에서 끝나므로 auth-service 없이 전체 경로를 테스트할 수 있다.
 */
class AuthenticationGlobalFilterTest {

    private static final String JWT_SECRET = "test-secret-key-for-gateway-jwt-decoding-0123456789";
    private static final String API_KEY = "test-api-key-value";

    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    private final ApiKeyProperties apiKeyProperties = new ApiKeyProperties();
    private final AuthenticationGlobalFilter filter = new AuthenticationGlobalFilter(
            new GatewayJwtDecoder(JWT_SECRET),
            new ApiKeyAuthenticator(apiKeyProperties),
            new RoleAuthorizationRules(),
            new ObjectMapper());

    private static String accessToken(long accountId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(accountId))
                .claim("email", "user" + accountId + "@pickbit.co.kr")
                .claim("nickname", "user" + accountId)
                .claim("role", role)
                .claim("provider", "LOCAL")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(SIGNING_KEY)
                .compact();
    }

    /** 체인까지 도달했는지, 도달했다면 어떤 요청이 넘어갔는지 기록하는 테스트용 체인. */
    private static final class RecordingChain implements GatewayFilterChain {
        private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            forwarded.set(exchange);
            return Mono.empty();
        }

        boolean wasCalled() {
            return forwarded.get() != null;
        }

        ServerWebExchange forwarded() {
            return forwarded.get();
        }
    }

    private MockServerWebExchange exchange(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
    }

    @Nested
    @DisplayName("비로그인 조회 허용")
    class PublicReads {

        @Test
        @DisplayName("상품 목록 GET은 토큰 없이 통과한다")
        void productListIsPublic() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products?page=0");

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isTrue();
        }

        @Test
        @DisplayName("경매 목록과 카테고리 GET도 토큰 없이 통과한다")
        void auctionAndCategoryListsArePublic() {
            for (String path : new String[]{"/api/auctions", "/api/auctions/1", "/api/categories"}) {
                RecordingChain chain = new RecordingChain();

                filter.filter(exchange(HttpMethod.GET, path), chain).block();

                assertThat(chain.wasCalled()).as(path).isTrue();
            }
        }

        @Test
        @DisplayName("공개 경로라도 클라이언트가 보낸 신원 헤더는 제거된다")
        void identityHeadersAreStrippedOnPublicPaths() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/products")
                            .header("X-User-Id", "99")
                            .header("X-User-Role", "ADMIN")
                            .header("nickname", "spoofed")
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isTrue();
            assertThat(chain.forwarded().getRequest().getHeaders().getFirst("X-User-Id")).isNull();
            assertThat(chain.forwarded().getRequest().getHeaders().getFirst("X-User-Role")).isNull();
            assertThat(chain.forwarded().getRequest().getHeaders().getFirst("nickname")).isNull();
        }
    }

    @Nested
    @DisplayName("인증 요구")
    class RequiresAuth {

        @Test
        @DisplayName("상품 등록 POST는 토큰 없이 401")
        void mutatingRequestsStillNeedAuth() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/products");

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("입찰 POST는 공개 조회 접두사에 걸리지만 401")
        void bidPostIsNotPublic() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/auctions/1/bids");

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("내 판매 목록 GET은 본인 자원이라 401")
        void mySellingIsNotPublic() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/products/me/selling");

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("내부 전용 API 차단")
    class InternalApi {

        @Test
        @DisplayName("토큰이 없어도 403으로 차단한다")
        void internalPathIsForbiddenWithoutToken() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange =
                    exchange(HttpMethod.PATCH, "/api/internal/products/1/status");

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("유효해 보이는 토큰을 들고 와도 403으로 차단한다")
        void internalPathIsForbiddenEvenWithToken() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.patch("/api/internal/products/1/status")
                            .header("Authorization", "Bearer some-token")
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("게이트웨이 자체 JWT 검증")
    class LocalJwtValidation {

        @Test
        @DisplayName("유효한 토큰은 통과하고 신원 헤더가 주입된다")
        void validTokenInjectsIdentityHeaders() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/products")
                            .header("Authorization", "Bearer " + accessToken(7L, "USER"))
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isTrue();
            HttpHeaders forwarded = chain.forwarded().getRequest().getHeaders();
            assertThat(forwarded.getFirst("X-User-Id")).isEqualTo("7");
            assertThat(forwarded.getFirst("X-User-Role")).isEqualTo("USER");
            assertThat(forwarded.getFirst("X-User-Email")).isEqualTo("user7@pickbit.co.kr");
        }

        @Test
        @DisplayName("다른 시크릿으로 서명된 토큰은 401")
        void tokenSignedWithAnotherSecretIsRejected() {
            String forged = Jwts.builder()
                    .subject("7")
                    .claim("email", "attacker@evil.com")
                    .claim("nickname", "attacker")
                    .claim("role", "ADMIN")
                    .claim("provider", "LOCAL")
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(Keys.hmacShaKeyFor(
                            "a-completely-different-secret-key-0123456789".getBytes(StandardCharsets.UTF_8)))
                    .compact();

            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/products")
                            .header("Authorization", "Bearer " + forged)
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("만료된 토큰은 401")
        void expiredTokenIsRejected() {
            String expired = Jwts.builder()
                    .subject("7")
                    .claim("email", "user7@pickbit.co.kr")
                    .claim("nickname", "user7")
                    .claim("role", "USER")
                    .claim("provider", "LOCAL")
                    .expiration(Date.from(Instant.now().minusSeconds(60)))
                    .signWith(SIGNING_KEY)
                    .compact();

            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/products")
                            .header("Authorization", "Bearer " + expired)
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("refresh token으로는 API를 호출할 수 없다")
        void refreshTokenIsRejected() {
            String refresh = Jwts.builder()
                    .subject("7")
                    .claim("type", "refresh")
                    .claim("provider", "LOCAL")
                    .claim("email", "user7@pickbit.co.kr")
                    .claim("nickname", "user7")
                    .claim("role", "USER")
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith(SIGNING_KEY)
                    .compact();

            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/products")
                            .header("Authorization", "Bearer " + refresh)
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("API key 우회")
    class ApiKeyBypass {

        @Test
        @DisplayName("비활성 상태에서는 올바른 키를 보내도 401")
        void disabledByDefault() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/products")
                            .header("X-Api-Key", API_KEY)
                            .header("X-Api-User-Id", "42")
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("키가 비어 있으면 enabled여도 비활성이다")
        void blankKeyMeansDisabled() {
            apiKeyProperties.setEnabled(true);
            apiKeyProperties.setKey("");

            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/products")
                            .header("X-Api-Key", "anything")
                            .header("X-Api-User-Id", "42")
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("활성 상태에서 올바른 키는 지정한 사용자로 통과한다")
        void validKeyImpersonatesUser() {
            apiKeyProperties.setEnabled(true);
            apiKeyProperties.setKey(API_KEY);

            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/products")
                            .header("X-Api-Key", API_KEY)
                            .header("X-Api-User-Id", "42")
                            .header("X-Api-Nickname", "tester42")
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isTrue();
            HttpHeaders forwarded = chain.forwarded().getRequest().getHeaders();
            assertThat(forwarded.getFirst("X-User-Id")).isEqualTo("42");
            assertThat(forwarded.getFirst("X-User-Role")).isEqualTo("USER");
            // API key 헤더는 게이트웨이에서 소비하고 다운스트림으로 넘기지 않는다
            assertThat(forwarded.getFirst("X-Api-Key")).isNull();
            assertThat(forwarded.getFirst("X-Api-User-Id")).isNull();
        }

        @Test
        @DisplayName("틀린 키는 401")
        void wrongKeyIsRejected() {
            apiKeyProperties.setEnabled(true);
            apiKeyProperties.setKey(API_KEY);

            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/products")
                            .header("X-Api-Key", "wrong-key")
                            .header("X-Api-User-Id", "42")
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("사용자 ID를 지정하지 않으면 401")
        void missingUserIdIsRejected() {
            apiKeyProperties.setEnabled(true);
            apiKeyProperties.setKey(API_KEY);

            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/products")
                            .header("X-Api-Key", API_KEY)
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("allow-admin-role이 꺼져 있으면 ADMIN 역할 요청은 401")
        void adminRoleBlockedWhenNotAllowed() {
            apiKeyProperties.setEnabled(true);
            apiKeyProperties.setKey(API_KEY);
            apiKeyProperties.setAllowAdminRole(false);

            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/categories")
                            .header("X-Api-Key", API_KEY)
                            .header("X-Api-User-Id", "42")
                            .header("X-Api-Role", "ADMIN")
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("역할 인가")
    class RoleAuthorization {

        @Test
        @DisplayName("일반 사용자는 카테고리를 만들 수 없다")
        void userCannotCreateCategory() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/categories")
                            .header("Authorization", "Bearer " + accessToken(7L, "USER"))
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("관리자는 카테고리를 만들 수 있다")
        void adminCanCreateCategory() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/categories")
                            .header("Authorization", "Bearer " + accessToken(1L, "ADMIN"))
                            .build());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isTrue();
        }

        @Test
        @DisplayName("카테고리 조회는 비로그인도 가능하다")
        void categoryReadStaysPublic() {
            RecordingChain chain = new RecordingChain();

            filter.filter(exchange(HttpMethod.GET, "/api/categories"), chain).block();

            assertThat(chain.wasCalled()).isTrue();
        }
    }

    @Nested
    @DisplayName("기존 공개 경로 유지")
    class ExistingPublicPaths {

        @Test
        @DisplayName("인증/헬스체크/WebSocket 핸드셰이크는 메서드와 무관하게 통과한다")
        void alwaysPublicPaths() {
            record Case(HttpMethod method, String path) {
            }
            Case[] cases = {
                    new Case(HttpMethod.POST, "/api/auth/login"),
                    new Case(HttpMethod.GET, "/actuator/health"),
                    new Case(HttpMethod.POST, "/api/auctions/ws/123/xhr_send"),
                    new Case(HttpMethod.GET, "/v3/api-docs/auth-service"),
            };

            for (Case c : cases) {
                RecordingChain chain = new RecordingChain();

                filter.filter(exchange(c.method(), c.path()), chain).block();

                assertThat(chain.wasCalled()).as(c.path()).isTrue();
            }
        }

        @Test
        @DisplayName("메트릭 엔드포인트는 공개되지 않는다")
        void prometheusEndpointIsNotPublic() {
            RecordingChain chain = new RecordingChain();
            MockServerWebExchange exchange = exchange(HttpMethod.GET, "/actuator/prometheus");

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isFalse();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
