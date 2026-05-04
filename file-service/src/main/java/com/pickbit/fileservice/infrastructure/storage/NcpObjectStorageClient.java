package com.pickbit.fileservice.infrastructure.storage;

import com.pickbit.fileservice.config.S3Properties;
import com.pickbit.fileservice.exception.StorageUploadException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class NcpObjectStorageClient {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public String upload(String key, byte[] bytes, String contentType, Visibility visibility) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .key(key)
                    .contentType(contentType)
                    .acl(visibility == Visibility.PUBLIC
                            ? ObjectCannedACL.PUBLIC_READ
                            : ObjectCannedACL.PRIVATE)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (S3Exception e) {
            throw new StorageUploadException("NCP Object Storage 업로드 실패: " + key, e);
        } catch (RuntimeException e) {
            throw new StorageUploadException("NCP Object Storage 업로드 중 오류 발생: " + key, e);
        }

        return "%s/%s/%s".formatted(s3Properties.endpoint(), s3Properties.bucketName(), key);
    }

    public URL presignedGetUrl(String key, Duration ttl) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url();
    }
}
