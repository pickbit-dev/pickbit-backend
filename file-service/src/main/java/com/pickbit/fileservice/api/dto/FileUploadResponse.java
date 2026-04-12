package com.pickbit.fileservice.api.dto;

public record FileUploadResponse(
        String fileUrl,
        String originalFilename,
        long size
) {
}
