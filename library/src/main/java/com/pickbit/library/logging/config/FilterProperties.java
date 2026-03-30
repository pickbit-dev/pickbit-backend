package com.pickbit.library.logging.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * Configuration properties for WSA filters
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wsa.filter")
public class FilterProperties {

    /**
     * Paths to exclude from logging
     */
    private String[] excludedPaths = {"/actuator", "/health", "/favicon.ico", "/static", "/css", "/js", "/images"};

    /**
     * Maximum size of request body to log (in bytes)
     */
    private int requestBodyLimit = 102400; // 100KB

    /**
     * Maximum size of response body to log (in bytes)
     */
    private int responseBodyLimit = 10240; // 10KB
}
