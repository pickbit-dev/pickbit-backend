package com.pickbit.fileservice.api;

import com.pickbit.fileservice.api.dto.FileUploadResponse;
import com.pickbit.fileservice.application.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


/**
 * 파일 업로드 API를 제공하는 컨트롤러입니다.
 * <p>
 * 현재는 이미지 업로드 기능을 제공하며,
 * 업로드된 파일은 외부 스토리지(S3 등)에 저장되고 접근 가능한 URL을 반환합니다.
 */
@Tag(name = "File", description = "파일 업로드 API")
@RestController
@RequestMapping("api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;

    /**
     * 이미지 파일을 업로드합니다.
     *
     * 멀티파트 요청으로 여러 개의 이미지 파일을 동시에 업로드할 수 있습니다.
     * 업로드 성공 시 각 파일의 접근 URL과 메타데이터를 반환합니다.
     *
     * @param files 업로드할 이미지 파일 목록
     * @return 업로드된 파일 정보 리스트
     */
    @Operation(
            summary = "이미지 업로드",
            description = "멀티파트 요청을 통해 여러 개의 이미지 파일을 업로드하고, 업로드된 파일의 URL 정보를 반환합니다."
    )
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<FileUploadResponse>> uploadImages(
            @Parameter(description = "업로드할 이미지 파일 목록", required = true)
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fileUploadService.uploadImages(files));
    }
}