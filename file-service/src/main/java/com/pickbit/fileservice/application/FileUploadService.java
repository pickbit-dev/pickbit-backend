package com.pickbit.fileservice.application;

import com.pickbit.fileservice.api.dto.FileUploadResponse;
import com.pickbit.fileservice.exception.InvalidFileException;
import com.pickbit.fileservice.infrastructure.storage.NcpObjectStorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileUploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L;

    private final NcpObjectStorageClient ncpObjectStorageClient;

    public List<FileUploadResponse> uploadImages(List<MultipartFile> files) {
        return files.stream()
                .map(this::uploadSingle)
                .toList();
    }

    private FileUploadResponse uploadSingle(MultipartFile file) {
        validate(file);

        String key = buildObjectKey(file);
        String contentType = file.getContentType();

        try {
            String fileUrl = ncpObjectStorageClient.upload(key, file.getInputStream(), contentType);
            return new FileUploadResponse(fileUrl, file.getOriginalFilename(), file.getSize());
        } catch (IOException e) {
            throw new InvalidFileException("파일을 읽는 중 오류가 발생했습니다: " + file.getOriginalFilename());
        }
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidFileException("업로드할 파일이 비어있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("파일 크기는 10MB를 초과할 수 없습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidFileException("지원하지 않는 이미지 형식입니다. (지원 형식: JPEG, PNG, WebP, GIF)");
        }
    }

    private String buildObjectKey(MultipartFile file) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        return "files/" + UUID.randomUUID() + (extension != null ? "." + extension.toLowerCase() : "");
    }
}
