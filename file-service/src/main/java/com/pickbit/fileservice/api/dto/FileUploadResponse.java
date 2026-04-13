package com.pickbit.fileservice.api.dto;

/**
 * 파일 업로드 응답 DTO.
 *
 * <p>파일 업로드 완료 후 클라이언트에 반환되는 업로드 결과 정보입니다.
 *
 * @param fileUrl          업로드된 파일의 접근 URL
 * @param originalFilename 원본 파일명
 * @param size             파일 크기 (바이트)
 */
public record FileUploadResponse(
        String fileUrl,
        String originalFilename,
        long size
) {
}
