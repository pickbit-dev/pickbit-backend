package com.pickbit.library.logging;

import com.pickbit.library.logging.config.FilterProperties;
import com.pickbit.library.logging.logger.LoggingFilter;
import com.pickbit.library.logging.logger.RequestLogger;
import com.pickbit.library.logging.logger.ResponseLogger;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(FilterProperties.class)
public class LoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RequestLogger requestLogger(ObjectMapper objectMapper) {
        return new RequestLogger(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResponseLogger responseLogger(ObjectMapper objectMapper) {
        return new ResponseLogger(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoggingFilter loggingFilter(
            RequestLogger requestLogger,
            ResponseLogger responseLogger,
            FilterProperties filterProperties
    ) {
        return new LoggingFilter(requestLogger, responseLogger, filterProperties);
    }
}
