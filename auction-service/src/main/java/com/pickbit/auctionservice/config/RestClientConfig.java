package com.pickbit.auctionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${client.product-service.base-url}")
    private String productServiceBaseUrl;

    @Bean
    public RestClient productServiceRestClient() {
        return RestClient.builder()
                .baseUrl(productServiceBaseUrl)
                .build();
    }
}
