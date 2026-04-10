package com.pickbit.productservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickbit.library.logging.config.FilterProperties;
import com.pickbit.library.logging.logger.LoggingFilter;
import com.pickbit.library.logging.logger.RequestLogger;
import com.pickbit.library.logging.logger.ResponseLogger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FilterProperties.class)
public class LoggingConfig {

    @Bean
    public RequestLogger requestLogger(ObjectMapper objectMapper) {
        return new RequestLogger(objectMapper);
    }

    @Bean
    public ResponseLogger responseLogger(ObjectMapper objectMapper) {
        return new ResponseLogger(objectMapper);
    }

    @Bean
    public LoggingFilter loggingFilter(RequestLogger requestLogger,
                                       ResponseLogger responseLogger,
                                       FilterProperties filterProperties) {
        return new LoggingFilter(requestLogger, responseLogger, filterProperties);
    }
}
