package com.pickbit.paymentservice;

import com.pickbit.paymentservice.config.TestContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestContainerConfig.class)
@ActiveProfiles("test")
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
