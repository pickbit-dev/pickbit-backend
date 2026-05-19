package com.pickbit.paymentservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Configuration
public class RestClientConfig {

    @Value("${client.toss-payments.base-url}")
    private String tossBaseUrl;

    @Value("${client.toss-payments.secret-key}")
    private String tossSecretKey;

    @Value("${client.toss-payments.connect-timeout-ms:500}")
    private long tossConnectTimeoutMs;

    @Value("${client.toss-payments.read-timeout-ms:5000}")
    private long tossReadTimeoutMs;

    @Bean
    public RestClient tossPaymentsRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(tossConnectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(tossReadTimeoutMs));

        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl(tossBaseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
