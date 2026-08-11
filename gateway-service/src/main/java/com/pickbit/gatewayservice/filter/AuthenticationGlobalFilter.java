package com.pickbit.gatewayservice.filter;

import com.pickbit.gatewayservice.security.ApiKeyAuthenticator;
import com.pickbit.gatewayservice.security.AuthenticatedUser;
import com.pickbit.gatewayservice.security.GatewayJwtDecoder;
import com.pickbit.gatewayservice.security.InvalidTokenException;
import com.pickbit.gatewayservice.security.RoleAuthorizationRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final String INTERNAL_PATH_PREFIX = "/api/internal";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_NICKNAME_HEADER = "X-User-Nickname";
    private static final String USER_NICKNAME_ENCODED_HEADER = "X-User-Nickname-Encoded";
    private static final String USER_PROVIDER_HEADER = "X-User-Provider";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String LEGACY_NICKNAME_HEADER = "nickname";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth",
            "/api/auctions/ws",
            "/oauth2",
            "/login/oauth2",
            // 액추에이터는 health/info 만 연다. "/actuator" 전체를 열면
            // /actuator/prometheus 로 내부 메트릭이 인증 없이 새어 나간다.
            // (Prometheus 는 내부 네트워크에서 각 서비스를 직접 긁으므로 영향 없다)
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/swagger-ui");

    /**
     * 비로그인 방문자도 카탈로그를 둘러볼 수 있어야 하는 경로. 조회(GET)만 열고
     * 생성/수정/삭제는 그대로 인증을 요구한다.
     */
    private static final List<String> PUBLIC_READ_PATHS = List.of(
            "/api/products",
            "/api/auctions",
            "/api/categories");

    /**
     * {@link #PUBLIC_READ_PATHS}의 접두사에 걸리지만 호출자 본인의 자원을 돌려주는 경로라
     * 공개하면 안 되는 예외 목록.
     */
    private static final List<String> AUTHENTICATED_READ_PATHS = List.of(
            "/api/products/me");

    private final GatewayJwtDecoder jwtDecoder;
    private final ApiKeyAuthenticator apiKeyAuthenticator;
    private final RoleAuthorizationRules authorizationRules;
    private final ObjectMapper objectMapper;

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 서비스 간 호출은 컨테이너 DNS로 직접 오가므로 게이트웨이를 통해 들어온 내부 API 요청은
        // 외부에서 온 것으로 간주하고 차단한다.
        if (path.startsWith(INTERNAL_PATH_PREFIX)) {
            return writeProblem(exchange, HttpStatus.FORBIDDEN, "내부 전용 API입니다.");
        }

        if (isPublicPath(path, request.getMethod())) {
            // 공개 경로에서도 클라이언트가 직접 보낸 신원 헤더는 반드시 지운다.
            // 그대로 흘려보내면 누구나 X-User-Id를 위조할 수 있다.
            ServerHttpRequest anonymousRequest = request.mutate()
                    .headers(AuthenticationGlobalFilter::removeIdentityHeaders)
                    .build();
            return chain.filter(exchange.mutate().request(anonymousRequest).build());
        }

        AuthenticatedUser user;
        try {
            user = authenticate(request);
        } catch (InvalidTokenException e) {
            return writeProblem(exchange, HttpStatus.UNAUTHORIZED, e.getMessage());
        }

        if (!authorizationRules.isAllowed(request.getMethod(), path, user.role())) {
            return writeProblem(exchange, HttpStatus.FORBIDDEN, "이 작업을 수행할 권한이 없습니다.");
        }

        ServerHttpRequest modifiedRequest = request.mutate()
                .headers(headers -> {
                    removeIdentityHeaders(headers);
                    removeApiKeyHeaders(headers);
                    headers.set(USER_ID_HEADER, String.valueOf(user.accountId()));
                    headers.set(USER_ROLE_HEADER, user.role());
                    if (StringUtils.hasText(user.nickname())) {
                        headers.set(USER_NICKNAME_ENCODED_HEADER, encodeHeaderValue(user.nickname()));
                    }
                    headers.set(USER_PROVIDER_HEADER, user.provider());
                    headers.set(USER_EMAIL_HEADER, user.email());
                })
                .build();
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    /**
     * API key 우회 경로가 활성이면 그쪽을 먼저 보고, 아니면 JWT를 로컬에서 검증한다.
     * 두 경우 모두 네트워크 호출이 없다.
     */
    private AuthenticatedUser authenticate(ServerHttpRequest request) {
        if (apiKeyAuthenticator.isPresent(request.getHeaders())) {
            return apiKeyAuthenticator.authenticate(request.getHeaders());
        }

        String token = resolveAccessToken(request);
        if (!StringUtils.hasText(token)) {
            throw new InvalidTokenException("인증 토큰이 필요합니다.");
        }
        return jwtDecoder.decode(token);
    }

    private String resolveAccessToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return request.getCookies().getFirst(ACCESS_TOKEN_COOKIE) == null
                ? null
                : request.getCookies().getFirst(ACCESS_TOKEN_COOKIE).getValue();
    }

    private String encodeHeaderValue(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void removeIdentityHeaders(HttpHeaders headers) {
        headers.remove(USER_ID_HEADER);
        headers.remove(USER_ROLE_HEADER);
        headers.remove(USER_NICKNAME_HEADER);
        headers.remove(USER_NICKNAME_ENCODED_HEADER);
        headers.remove(USER_PROVIDER_HEADER);
        headers.remove(USER_EMAIL_HEADER);
        headers.remove(LEGACY_NICKNAME_HEADER);
    }

    /** API key와 그 부가 헤더는 게이트웨이에서 소비하고 다운스트림으로 넘기지 않는다. */
    private static void removeApiKeyHeaders(HttpHeaders headers) {
        headers.remove(ApiKeyAuthenticator.API_KEY_HEADER);
        headers.remove(ApiKeyAuthenticator.API_USER_ID_HEADER);
        headers.remove(ApiKeyAuthenticator.API_NICKNAME_HEADER);
        headers.remove(ApiKeyAuthenticator.API_ROLE_HEADER);
        headers.remove(ApiKeyAuthenticator.API_EMAIL_HEADER);
    }

    private boolean isPublicPath(String path, HttpMethod method) {
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return true;
        }
        if (!HttpMethod.GET.equals(method)) {
            return false;
        }
        if (AUTHENTICATED_READ_PATHS.stream().anyMatch(path::startsWith)) {
            return false;
        }
        return PUBLIC_READ_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> writeProblem(ServerWebExchange exchange, HttpStatus status, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, normalizeDetail(detail, status));
        problem.setProperty("timestamp", LocalDateTime.now());
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(problem);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            byte[] bytes = ("{\"detail\":\"" + status.getReasonPhrase() + "\"}").getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        }
    }

    private String normalizeDetail(String detail, HttpStatus status) {
        if (!StringUtils.hasText(detail)) {
            return status.getReasonPhrase();
        }
        return detail.length() > 500 ? detail.substring(0, 500) : detail;
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
