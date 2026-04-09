package com.pickbit.productservice.exception;

import com.pickbit.library.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ProductExceptionHandler extends GlobalExceptionHandler {

    @ExceptionHandler({
            ProductNotFoundException.class,
            CategoryNotFoundException.class
    })
    protected ResponseEntity<ProblemDetail> handleNotFound(RuntimeException e, HttpServletRequest request) {
        logException(HttpStatus.NOT_FOUND, e);
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedProductAccessException.class)
    protected ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedProductAccessException e, HttpServletRequest request) {
        logException(HttpStatus.FORBIDDEN, e);
        return buildResponse(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidImageFileException.class)
    protected ResponseEntity<ProblemDetail> handleInvalidImageFile(InvalidImageFileException e, HttpServletRequest request) {
        logException(HttpStatus.BAD_REQUEST, e);
        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(StorageUploadException.class)
    protected ResponseEntity<ProblemDetail> handleStorageUpload(StorageUploadException e, HttpServletRequest request) {
        logException(HttpStatus.INTERNAL_SERVER_ERROR, e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드 중 오류가 발생했습니다.", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    protected ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        logException(HttpStatus.BAD_REQUEST, e);
        return buildResponse(HttpStatus.BAD_REQUEST, "파일 크기가 허용 한도를 초과했습니다.", request);
    }
}
