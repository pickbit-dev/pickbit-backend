package com.pickbit.productservice.infrastructure.storage;

import com.pickbit.productservice.config.S3Properties;
import com.pickbit.productservice.exception.StorageUploadException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class NcpObjectStorageClient {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public String upload(String key, InputStream inputStream, String contentType) {
        try {
            byte[] bytes = inputStream.readAllBytes();

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (S3Exception | IOException e) {
            throw new StorageUploadException("NCP Object Storage 업로드 실패: " + key, e);
        }

        return "%s/%s/%s".formatted(s3Properties.endpoint(), s3Properties.bucketName(), key);
    }
}
