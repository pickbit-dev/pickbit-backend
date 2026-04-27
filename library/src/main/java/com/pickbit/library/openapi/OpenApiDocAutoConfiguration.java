package com.pickbit.library.openapi;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnProperty(name = "openapi.docs-export.enabled", havingValue = "true")
public class OpenApiDocAutoConfiguration {

    @Bean
    public OpenApiDocExporter openApiDocExporter(Environment env) {
        return new OpenApiDocExporter(env);
    }
}
