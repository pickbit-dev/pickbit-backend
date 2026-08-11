package com.pickbit.library.inbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * 인박스 재처리 스케줄러를 등록합니다.
 *
 * <p>{@link InboxRetryStore} 를 구현한 빈이 있는 서비스에서만 동작합니다.
 * Kafka 를 소비하지 않는 서비스(gateway, file, auction)에는 아무 영향이 없습니다.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "inbox.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(InboxRetryProperties.class)
@EnableScheduling
public class InboxRetryAutoConfiguration {

    @Bean
    @ConditionalOnBean(InboxRetryStore.class)
    public InboxRetryScheduler inboxRetryScheduler(
            InboxRetryProperties properties,
            InboxRetryStore store,
            List<InboxEventHandler> handlers) {
        return new InboxRetryScheduler(properties, store, handlers);
    }

    /**
     * Kafka 의존 부분은 중첩 클래스로 분리한다.
     *
     * <p>바깥 클래스의 메서드 시그니처에 Kafka 타입이 있으면, 조건을 평가하기도 전에
     * 클래스 introspection 단계에서 {@code NoClassDefFoundError} 가 난다.
     * Kafka 를 쓰지 않는 서비스(auction 등)의 컨텍스트가 통째로 기동에 실패한다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.kafka.listener.ConsumerRecordRecoverer")
    static class KafkaRecovererConfiguration {

        /**
         * 인라인 재시도 소진 시 실패를 인박스에 남기는 recoverer.
         * 지정하지 않으면 Spring Kafka 기본 recoverer 가 로그만 찍고 오프셋을 넘겨 이벤트가 사라진다.
         */
        @Bean
        @ConditionalOnBean(InboxRetryStore.class)
        ExhaustedRetryRecoverer exhaustedRetryRecoverer(InboxRetryStore store) {
            return new ExhaustedRetryRecoverer(store);
        }
    }
}
