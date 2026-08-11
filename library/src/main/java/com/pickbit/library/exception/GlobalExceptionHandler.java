package com.pickbit.library.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
public abstract class GlobalExceptionHandler {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    protected void logException(HttpStatus status, Throwable e) {
        if (status.is5xxServerError()) {
            log.error("Unhandled exception [{}]: {}", status.value(), e.getMessage(), e);
        } else {
            log.warn("Handled exception [{}]: {}", status.value(), e.getMessage());
        }
    }

    protected ProblemDetail createProblemDetail(HttpStatus status, String message, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        if (request != null) {
            pd.setProperty("path", request.getRequestURI());
        }
        return pd;
    }

    protected ResponseEntity<ProblemDetail> buildResponse(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(createProblemDetail(status, message, request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        logException(HttpStatus.BAD_REQUEST, e);

        String message = e.getBindingResult().getFieldErrors().isEmpty()
                ? "입력 값 검증에 실패했습니다."
                : e.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        ProblemDetail pd = createProblemDetail(HttpStatus.BAD_REQUEST, message, request);
        if (!fieldErrors.isEmpty()) pd.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(BindException.class)
    protected ResponseEntity<ProblemDetail> handleBindException(BindException e, HttpServletRequest request) {
        logException(HttpStatus.BAD_REQUEST, e);

        String message = e.getBindingResult().getFieldErrors().isEmpty()
                ? "데이터 바인딩에 실패했습니다."
                : e.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        ProblemDetail pd = createProblemDetail(HttpStatus.BAD_REQUEST, message, request);
        if (!fieldErrors.isEmpty()) pd.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        logException(HttpStatus.BAD_REQUEST, e);
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("제약 조건 위반이 발생했습니다.");
        return buildResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ProblemDetail> handleMissingServletRequestParameter(MissingServletRequestParameterException e, HttpServletRequest request) {
        logException(HttpStatus.BAD_REQUEST, e);
        return buildResponse(HttpStatus.BAD_REQUEST, "필수 파라미터 '%s'가 누락되었습니다.".formatted(e.getParameterName()), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        logException(HttpStatus.BAD_REQUEST, e);
        String typeName = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "Unknown";
        return buildResponse(HttpStatus.BAD_REQUEST, "파라미터 '%s'의 타입이 올바르지 않습니다. 예상 타입: %s".formatted(e.getName(), typeName), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ProblemDetail> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logException(HttpStatus.METHOD_NOT_ALLOWED, e);
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage(), request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    protected ResponseEntity<ProblemDetail> handleNoHandlerFound(NoHandlerFoundException e, HttpServletRequest request) {
        logException(HttpStatus.NOT_FOUND, e);
        return buildResponse(HttpStatus.NOT_FOUND, "요청한 URL을 찾을 수 없습니다: %s %s".formatted(e.getHttpMethod(), e.getRequestURL()), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<ProblemDetail> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        logException(HttpStatus.NOT_FOUND, e);
        return buildResponse(HttpStatus.NOT_FOUND, "요청하신 리소스를 찾을 수 없습니다.", request);
    }

    @ExceptionHandler(ForbiddenException.class)
    protected ResponseEntity<ProblemDetail> handleForbiddenException(ForbiddenException e, HttpServletRequest request) {
        logException(HttpStatus.FORBIDDEN, e);
        return buildResponse(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        logException(HttpStatus.BAD_REQUEST, e);
        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage() != null ? e.getMessage() : "잘못된 인수가 전달되었습니다.", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    protected ResponseEntity<ProblemDetail> handleIllegalStateException(IllegalStateException e, HttpServletRequest request) {
        logException(HttpStatus.CONFLICT, e);
        return buildResponse(HttpStatus.CONFLICT, e.getMessage() != null ? e.getMessage() : "잘못된 상태입니다.", request);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ProblemDetail> handleException(Exception e, HttpServletRequest request) {
        logException(HttpStatus.INTERNAL_SERVER_ERROR, e);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", request);
    }
}