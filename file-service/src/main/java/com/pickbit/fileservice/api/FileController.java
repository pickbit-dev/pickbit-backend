package com.pickbit.fileservice.api;

import com.pickbit.fileservice.api.dto.FileUploadResponse;
import com.pickbit.fileservice.application.FileUploadService;
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

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<FileUploadResponse>> uploadImages(
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileUploadService.uploadImages(files));
    }
}
