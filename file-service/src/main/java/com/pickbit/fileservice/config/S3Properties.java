package com.pickbit.fileservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NCP Object Storage 연결 설정 프로퍼티.
 *
 * <p>{@code application.yml}의 {@code ncp.storage.*} 값을 바인딩하며,
 * {@link com.pickbit.fileservice.infrastructure.storage.NcpObjectStorageClient} 초기화에 사용된다.
 *
 * @param accessKey  NCP Access Key
 * @param secretKey  NCP Secret Key
 * @param endpoint   Object Storage 엔드포인트 URL
 * @param region     버킷이 위치한 리전
 * @param bucketName 파일을 저장할 버킷 이름
 */
@ConfigurationProperties(prefix = "ncp.storage")
public record S3Properties(
        String accessKey,
        String secretKey,
        String endpoint,
        String region,
        String bucketName
) {}
