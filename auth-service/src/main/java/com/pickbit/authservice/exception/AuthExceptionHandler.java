package com.pickbit.authservice.exception;

import com.pickbit.library.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler extends GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    protected ResponseEntity<ProblemDetail> handleDuplicateEmail(DuplicateEmailException e, HttpServletRequest request) {
        logException(HttpStatus.CONFLICT, e);
        return buildResponse(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler({InvalidCredentialException.class, InvalidTokenException.class})
    protected ResponseEntity<ProblemDetail> handleUnauthorized(RuntimeException e, HttpServletRequest request) {
        logException(HttpStatus.UNAUTHORIZED, e);
        return buildResponse(HttpStatus.UNAUTHORIZED, e.getMessage(), request);
    }
}
