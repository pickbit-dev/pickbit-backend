package com.pickbit.userservice.exception;

import com.pickbit.library.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserExceptionHandler extends GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    protected ResponseEntity<ProblemDetail> handleNotFound(UserNotFoundException e, HttpServletRequest request) {
        logException(HttpStatus.NOT_FOUND, e);
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(DuplicateNicknameException.class)
    protected ResponseEntity<ProblemDetail> handleDuplicateNickname(DuplicateNicknameException e, HttpServletRequest request) {
        logException(HttpStatus.CONFLICT, e);
        return buildResponse(HttpStatus.CONFLICT, e.getMessage(), request);
    }
}
