package com.pickbit.library.auth;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthContextFilter authContextFilter() {
        return new AuthContextFilter();
    }
}
