package com.pickbit.productservice.exception;

import com.pickbit.library.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProductExceptionHandler extends GlobalExceptionHandler {

    @ExceptionHandler({ProductNotFoundException.class, CategoryNotFoundException.class})
    protected ResponseEntity<ProblemDetail> handleNotFound(RuntimeException e, HttpServletRequest request) {
        logException(HttpStatus.NOT_FOUND, e);
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedProductAccessException.class)
    protected ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedProductAccessException e, HttpServletRequest request) {
        logException(HttpStatus.FORBIDDEN, e);
        return buildResponse(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    protected ResponseEntity<ProblemDetail> handleDuplicateCategory(DuplicateCategoryException e, HttpServletRequest request) {
        logException(HttpStatus.CONFLICT, e);
        return buildResponse(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidProductStatusException.class)
    protected ResponseEntity<ProblemDetail> handleInvalidProductStatus(InvalidProductStatusException e, HttpServletRequest request) {
        logException(HttpStatus.CONFLICT, e);
        return buildResponse(HttpStatus.CONFLICT, e.getMessage(), request);
    }
}
