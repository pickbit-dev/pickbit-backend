package com.pickbit.library.maintenance;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.persistence.EntityManagerFactory;

/**
 * 보관 기간 정리 기능을 JPA 를 실제로 쓰는 서비스에만 등록합니다.
 *
 * <p>{@code maintenance.retention.targets} 가 비어 있으면 스케줄러가 아무 일도 하지 않으므로,
 * 정리 대상이 없는 서비스는 설정을 두지 않으면 됩니다.
 *
 * <p><b>클래스 존재가 아니라 빈 존재로 판단해야 한다.</b> {@code library} 가
 * {@code api 'spring-data-jpa'} 로 JPA 를 노출하기 때문에 DB 를 쓰지 않는 서비스
 * (file-service, gateway-service)에도 {@code jakarta.persistence} 클래스가 올라온다.
 * 예전처럼 {@code @ConditionalOnClass} 로 막으면 조건을 통과해 {@link RetentionCleaner} 가
 * 등록되고, {@code EntityManagerFactory} 빈이 없어 컨텍스트가 통째로 기동에 실패한다.
 * 실제로 file-service 가 이 이유로 재시작 루프에 빠졌다.
 *
 * <p><b>{@code @ComponentScan} 을 쓰지 않는 이유.</b> 스캔은 PARSE_CONFIGURATION 단계에서
 * 일어나는데 {@code @ConditionalOnBean} 은 REGISTER_BEAN 단계 조건이라, 둘을 같이 쓰면
 * Spring 이 {@code "Component scan ... could not be used with conditions in REGISTER_BEAN
 * phase"} 로 거부한다. 그래서 빈을 명시적으로 선언하고 조건을 빈 단위로 건다
 * ({@code InboxRetryAutoConfiguration} 과 같은 방식).
 *
 * <p>{@code afterName} 을 문자열로 쓰는 것도 의도적이다. 클래스 리터럴로 참조하면 해당
 * 자동설정이 없는 서비스에서 introspection 단계에 {@code NoClassDefFoundError} 가 난다.
 */
@AutoConfiguration(afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnProperty(prefix = "maintenance.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RetentionProperties.class)
@EnableScheduling
public class RetentionAutoConfiguration {

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    public RetentionCleaner retentionCleaner() {
        return new RetentionCleaner();
    }

    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    public RetentionScheduler retentionScheduler(RetentionProperties properties, RetentionCleaner cleaner) {
        return new RetentionScheduler(properties, cleaner);
    }
}