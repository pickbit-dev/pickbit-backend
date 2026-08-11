package com.pickbit.library.maintenance;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.persistence.EntityManager;

/**
 * 보관 기간 정리 기능을 JPA 를 쓰는 서비스에 자동으로 등록합니다.
 *
 * <p>{@code maintenance.retention.targets} 가 비어 있으면 스케줄러가 아무 일도 하지 않으므로,
 * 정리 대상이 없는 서비스는 설정을 두지 않으면 됩니다.
 */
@AutoConfiguration
@ConditionalOnClass(EntityManager.class)
@ConditionalOnProperty(prefix = "maintenance.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RetentionProperties.class)
@EnableScheduling
@ComponentScan(basePackageClasses = RetentionCleaner.class)
public class RetentionAutoConfiguration {
}
