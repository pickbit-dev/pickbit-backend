package com.pickbit.productservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${client.auction-service.base-url}")
    private String auctionServiceBaseUrl;

    @Value("${client.auction-service.connect-timeout-ms:500}")
    private long auctionConnectTimeoutMs;

    @Value("${client.auction-service.read-timeout-ms:2000}")
    private long auctionReadTimeoutMs;

    @Bean
    public RestClient auctionServiceRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(auctionConnectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(auctionReadTimeoutMs));

        return RestClient.builder()
                .baseUrl(auctionServiceBaseUrl)
                .requestFactory(factory)
                .build();
    }
}
