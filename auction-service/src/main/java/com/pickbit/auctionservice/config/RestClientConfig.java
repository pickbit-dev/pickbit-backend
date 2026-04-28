package com.pickbit.auctionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${client.product-service.base-url}")
    private String productServiceBaseUrl;

    @Value("${client.product-service.connect-timeout-ms:500}")
    private long connectTimeoutMs;

    @Value("${client.product-service.read-timeout-ms:2000}")
    private long readTimeoutMs;

    @Bean
    public RestClient productServiceRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(productServiceBaseUrl)
                .requestFactory(factory)
                .build();
    }
}