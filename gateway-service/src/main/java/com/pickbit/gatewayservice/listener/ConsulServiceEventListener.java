package com.pickbit.gatewayservice.listener;

import com.pickbit.gatewayservice.config.ConsulRouteDefinitionLocator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.consul.discovery.ConsulDiscoveryClient;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsulServiceEventListener {

    private final ConsulDiscoveryClient consulDiscoveryClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ConsulRouteDefinitionLocator routeDefinitionLocator;
    private final Set<String> previousServices = ConcurrentHashMap.newKeySet();
    private volatile boolean initialized = false;

    @EventListener
    public void handleHeartbeatEvent(HeartbeatEvent event) {
        try {
            List<String> currentServices = consulDiscoveryClient.getServices();
            Set<String> currentServiceSet = new HashSet<>(currentServices);

            if (!initialized) {
                previousServices.addAll(currentServiceSet);
                initialized = true;
                log.info("Consul 서비스 감지 초기화 완료. services={}", currentServices);
                return;
            }

            Set<String> newServices = new HashSet<>(currentServiceSet);
            newServices.removeAll(previousServices);

            Set<String> removedServices = new HashSet<>(previousServices);
            removedServices.removeAll(currentServiceSet);

            boolean changed = false;
            for (String serviceName : newServices) {
                routeDefinitionLocator.addRoute(serviceName);
                changed = true;
            }
            for (String serviceName : removedServices) {
                routeDefinitionLocator.removeRoute(serviceName);
                changed = true;
            }

            if (changed) {
                eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                log.info("Consul 라우트 갱신 완료. cachedRouteCount={}", routeDefinitionLocator.getCachedRouteCount());
            }

            previousServices.clear();
            previousServices.addAll(currentServiceSet);
        } catch (Exception e) {
            log.warn("Consul 서비스 이벤트 처리 실패: {}", e.getMessage());
        }
    }
}
