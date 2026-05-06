package com.pickbit.gatewayservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.consul.discovery.ConsulDiscoveryClient;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gateway.route.consul.enabled", havingValue = "true", matchIfMissing = true)
public class ConsulRouteDefinitionLocator implements RouteDefinitionLocator {

    private static final String GATEWAY_PATH_METADATA = "gateway-path";

    private final ConsulDiscoveryClient consulDiscoveryClient;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, RouteDefinition> cachedRoutes = new ConcurrentHashMap<>();
    private final Set<String> loggedRoutes = ConcurrentHashMap.newKeySet();

    @Override
    public @NonNull Flux<RouteDefinition> getRouteDefinitions() {
        return Flux.fromIterable(getConsulServices())
                .filter(this::shouldCreateRoute)
                .map(this::createRouteDefinition)
                .doOnNext(this::cacheRoute)
                .concatWith(Flux.fromIterable(cleanupOrphanedRoutes()))
                .onErrorResume(error -> {
                    log.error("Consul 라우트 생성 실패. 캐시 라우트로 대체합니다.", error);
                    return Flux.fromIterable(cachedRoutes.values());
                });
    }

    public void addRoute(String serviceName) {
        if (!shouldCreateRoute(serviceName)) {
            return;
        }
        RouteDefinition definition = createRouteDefinition(serviceName);
        cachedRoutes.put(definition.getId(), definition);
        cacheRoute(definition);
        publishRefreshEvent();
    }

    public void removeRoute(String serviceName) {
        String routeId = routeId(serviceName);
        if (cachedRoutes.remove(routeId) != null) {
            loggedRoutes.remove(routeId);
            log.info("Consul 라우트 제거: {}", routeId);
            publishRefreshEvent();
        }
    }

    public void clearRouteCache() {
        cachedRoutes.clear();
        loggedRoutes.clear();
    }

    public int getCachedRouteCount() {
        return cachedRoutes.size();
    }

    private List<String> getConsulServices() {
        try {
            return consulDiscoveryClient.getServices();
        } catch (Exception e) {
            log.warn("Consul 서비스 목록 조회 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean shouldCreateRoute(String serviceName) {
        return StringUtils.hasText(serviceName)
                && !serviceName.equals("gateway-service")
                && !serviceName.startsWith("consul");
    }

    private RouteDefinition createRouteDefinition(String serviceName) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(routeId(serviceName));
        definition.setUri(URI.create("lb://" + serviceName));

        PredicateDefinition pathPredicate = new PredicateDefinition();
        pathPredicate.setName("Path");
        pathPredicate.addArg("pattern", resolveGatewayPath(serviceName));
        definition.setPredicates(List.of(pathPredicate));
        definition.setFilters(List.of());
        return definition;
    }

    private String resolveGatewayPath(String serviceName) {
        try {
            List<ServiceInstance> instances = consulDiscoveryClient.getInstances(serviceName);
            for (ServiceInstance instance : instances) {
                String path = instance.getMetadata().get(GATEWAY_PATH_METADATA);
                if (StringUtils.hasText(path)) {
                    return path;
                }
            }
        } catch (Exception e) {
            log.warn("서비스 metadata 조회 실패. serviceName={}, error={}", serviceName, e.getMessage());
        }
        String prefix = serviceName.replace("-service", "");
        return "/api/" + prefix + "/**";
    }

    private void cacheRoute(RouteDefinition definition) {
        cachedRoutes.put(definition.getId(), definition);
        if (loggedRoutes.add(definition.getId())) {
            String path = definition.getPredicates().getFirst().getArgs().get("pattern");
            log.info("Consul 라우트 생성: {} -> {} ({})", definition.getId(), definition.getUri(), path);
        }
    }

    private Collection<RouteDefinition> cleanupOrphanedRoutes() {
        Set<String> validRouteIds = new HashSet<>();
        for (String service : getConsulServices()) {
            if (shouldCreateRoute(service)) {
                validRouteIds.add(routeId(service));
            }
        }
        cachedRoutes.entrySet().removeIf(entry -> {
            boolean orphaned = !validRouteIds.contains(entry.getKey());
            if (orphaned) {
                loggedRoutes.remove(entry.getKey());
                log.info("Consul 라우트 정리: {}", entry.getKey());
            }
            return orphaned;
        });
        return Collections.emptyList();
    }

    private void publishRefreshEvent() {
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
    }

    private String routeId(String serviceName) {
        return "consul-" + serviceName;
    }
}
