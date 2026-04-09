package com.pickbit.productservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ncp.storage")
public record S3Properties(
        String accessKey,
        String secretKey,
        String endpoint,
        String region,
        String bucketName
) {}
