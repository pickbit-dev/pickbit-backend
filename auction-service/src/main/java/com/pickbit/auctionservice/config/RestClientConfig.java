package com.pickbit.auctionservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${client.product-service.base-url}")
    private String productServiceBaseUrl;

    @Value("${client.product-service.connect-timeout-ms:500}")
    private long productConnectTimeoutMs;

    @Value("${client.product-service.read-timeout-ms:2000}")
    private long productReadTimeoutMs;

    @Value("${client.user-service.base-url}")
    private String userServiceBaseUrl;

    @Value("${client.user-service.connect-timeout-ms:500}")
    private long userConnectTimeoutMs;

    @Value("${client.user-service.read-timeout-ms:2000}")
    private long userReadTimeoutMs;

    @Bean
    public RestClient productServiceRestClient() {
        return buildClient(productServiceBaseUrl, productConnectTimeoutMs, productReadTimeoutMs);
    }

    @Bean
    public RestClient userServiceRestClient() {
        return buildClient(userServiceBaseUrl, userConnectTimeoutMs, userReadTimeoutMs);
    }

    private RestClient buildClient(String baseUrl, long connectTimeoutMs, long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
