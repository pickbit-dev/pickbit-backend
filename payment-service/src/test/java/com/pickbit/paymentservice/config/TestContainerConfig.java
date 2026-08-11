package com.pickbit.paymentservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 컨텍스트를 띄우는 테스트용 인프라입니다.
 *
 * <p>Redis 도 함께 띄운다. ShedLock 의 {@code RedisLockProvider} 가
 * {@code RedisConnectionFactory} 를 요구하기 때문에 MySQL 만으로는 컨텍스트가 뜨지 않는다.
 */
@TestConfiguration
@SuppressWarnings("resource")
public class TestContainerConfig {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4.5"))
                .withDatabaseName("test")
                .withUsername("test")
                .withPassword("test");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }
}
