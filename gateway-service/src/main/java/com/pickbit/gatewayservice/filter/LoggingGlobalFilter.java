package com.pickbit.gatewayservice.filter;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final int MAX_URL_LOG_LENGTH = 2000;

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        return chain.filter(exchange)
                .doOnSuccess(ignored -> logRequest(exchange, startTime))
                .doOnError(error -> logError(exchange, startTime, error));
    }

    private void logRequest(ServerWebExchange exchange, long startTime) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        int status = response.getStatusCode() != null ? response.getStatusCode().value() : 0;
        log.info("REQUEST | {} {} -> {} | status={} | duration={}ms",
                request.getMethod(),
                getRequestUrl(request),
                resolveServiceName(exchange),
                status,
                System.currentTimeMillis() - startTime);
    }

    private void logError(ServerWebExchange exchange, long startTime, Throwable error) {
        ServerHttpRequest request = exchange.getRequest();
        log.error("REQUEST | {} {} -> ERROR | duration={}ms | message={}",
                request.getMethod(),
                getRequestUrl(request),
                System.currentTimeMillis() - startTime,
                error.getMessage());
    }

    private String getRequestUrl(ServerHttpRequest request) {
        URI uri = request.getURI();
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        String url = query == null ? path : path + "?" + query;
        return url.length() <= MAX_URL_LOG_LENGTH ? url : url.substring(0, MAX_URL_LOG_LENGTH) + "...";
    }

    private String resolveServiceName(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return "UNKNOWN";
        }
        URI uri = route.getUri();
        return "lb".equals(uri.getScheme()) ? uri.getHost() : uri.toString();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
