package com.pickbit.productservice.api.dto.response;

public record ImageUploadResponse(
        String imageUrl,
        String originalFilename,
        long fileSize
) {}
