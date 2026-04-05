package com.pickbit.productservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.PageableDefault;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
