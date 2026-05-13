package com.pickbit.gatewayservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickbit.gatewayservice.dto.AuthValidateRequest;
import com.pickbit.gatewayservice.dto.AuthValidateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_SERVICE_VALIDATE_URL = "http://auth-service/api/auth/validate";
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USER_NICKNAME_HEADER = "X-User-Nickname";
    private static final String USER_PROVIDER_HEADER = "X-User-Provider";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String LEGACY_NICKNAME_HEADER = "nickname";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String token = resolveAccessToken(request);
        if (!StringUtils.hasText(token)) {
            return writeProblem(exchange, HttpStatus.UNAUTHORIZED, "인증 토큰이 필요합니다.");
        }

        return validate(token)
                .flatMap(user -> {
                    ServerHttpRequest modifiedRequest = request.mutate()
                            .headers(headers -> {
                                headers.remove(USER_ID_HEADER);
                                headers.remove(USER_ROLE_HEADER);
                                headers.remove(USER_NICKNAME_HEADER);
                                headers.remove(USER_PROVIDER_HEADER);
                                headers.remove(USER_EMAIL_HEADER);
                                headers.remove(LEGACY_NICKNAME_HEADER);
                                headers.set(USER_ID_HEADER, String.valueOf(user.accountId()));
                                headers.set(USER_ROLE_HEADER, user.role());
                                headers.set(USER_NICKNAME_HEADER, user.nickname());
                                headers.set(USER_PROVIDER_HEADER, user.provider());
                                headers.set(USER_EMAIL_HEADER, user.email());
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                })
                .onErrorResume(WebClientResponseException.class, error -> {
                    HttpStatus status = HttpStatus.resolve(error.getStatusCode().value());
                    return writeProblem(exchange, status != null ? status : HttpStatus.UNAUTHORIZED, error.getResponseBodyAsString());
                })
                .onErrorResume(Exception.class, error -> {
                    log.warn("인증 처리 실패: {}", error.getMessage());
                    return writeProblem(exchange, HttpStatus.UNAUTHORIZED, "토큰 검증에 실패했습니다.");
                });
    }

    private Mono<AuthValidateResponse> validate(String token) {
        return webClientBuilder.build()
                .post()
                .uri(AUTH_SERVICE_VALIDATE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthValidateRequest(token))
                .retrieve()
                .bodyToMono(AuthValidateResponse.class);
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

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth")
                || path.startsWith("/oauth2")
                || path.startsWith("/login/oauth2")
                || path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
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
