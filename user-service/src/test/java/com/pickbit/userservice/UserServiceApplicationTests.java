package com.pickbit.userservice;

import com.pickbit.userservice.config.TestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestContainerConfig.class)
@ActiveProfiles("test")
class UserServiceApplicationTests {

    /**
     * 컨텍스트가 뜬다는 것은 Flyway 마이그레이션이 적용되고 Hibernate 의 {@code validate} 가
     * 통과했다는 뜻이다 — 즉 마이그레이션이 엔티티와 일치한다.
     */
    @Test
    void contextLoads() {
    }

}
