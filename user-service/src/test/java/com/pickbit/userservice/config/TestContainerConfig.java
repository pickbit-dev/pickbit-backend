package com.pickbit.userservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 컨텍스트를 띄우는 테스트용 MySQL 입니다.
 *
 * <p>H2 로는 대체할 수 없습니다. 스키마를 Flyway 마이그레이션이 만드는데 그 DDL 이
 * MySQL 문법이라 H2 에서는 실행되지 않습니다. 테스트가 프로덕션과 같은 경로로 스키마를
 * 만들어야 마이그레이션과 엔티티의 불일치를 CI 가 잡아줍니다.
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
}
