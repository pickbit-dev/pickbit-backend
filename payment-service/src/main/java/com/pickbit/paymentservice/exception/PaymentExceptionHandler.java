package com.pickbit.paymentservice.exception;

import com.pickbit.library.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentExceptionHandler extends GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    protected ResponseEntity<ProblemDetail> handleNotFound(PaymentNotFoundException e, HttpServletRequest request) {
        logException(HttpStatus.NOT_FOUND, e);
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler({
            InvalidPaymentStatusException.class,
            PaymentAmountMismatchException.class
    })
    protected ResponseEntity<ProblemDetail> handleConflict(RuntimeException e, HttpServletRequest request) {
        logException(HttpStatus.CONFLICT, e);
        return buildResponse(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(PaymentAccessDeniedException.class)
    protected ResponseEntity<ProblemDetail> handleForbidden(PaymentAccessDeniedException e, HttpServletRequest request) {
        logException(HttpStatus.FORBIDDEN, e);
        return buildResponse(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    protected ResponseEntity<ProblemDetail> handleUnauthorized(InvalidWebhookSignatureException e, HttpServletRequest request) {
        logException(HttpStatus.UNAUTHORIZED, e);
        return buildResponse(HttpStatus.UNAUTHORIZED, e.getMessage(), request);
    }

    @ExceptionHandler(TossPaymentApiException.class)
    protected ResponseEntity<ProblemDetail> handleTossApi(TossPaymentApiException e, HttpServletRequest request) {
        logException(HttpStatus.BAD_GATEWAY, e);
        ProblemDetail pd = createProblemDetail(HttpStatus.BAD_GATEWAY, e.getMessage(), request);
        pd.setProperty("pgErrorCode", e.getErrorCode());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(pd);
    }

    @ExceptionHandler(PgUnavailableException.class)
    protected ResponseEntity<ProblemDetail> handlePgUnavailable(PgUnavailableException e, HttpServletRequest request) {
        logException(HttpStatus.SERVICE_UNAVAILABLE, e);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    protected ResponseEntity<ProblemDetail> handleOptimisticLock(
            ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
        logException(HttpStatus.CONFLICT, e);
        return buildResponse(HttpStatus.CONFLICT,
                "동시 처리 충돌이 발생했습니다. 잠시 후 다시 시도해주세요.", request);
    }
}
