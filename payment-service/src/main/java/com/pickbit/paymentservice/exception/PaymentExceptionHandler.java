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

    @ExceptionHandler({PaymentNotFoundException.class, SettlementNotFoundException.class})
    protected ResponseEntity<ProblemDetail> handleNotFound(RuntimeException e, HttpServletRequest request) {
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

    @ExceptionHandler({PaymentAccessDeniedException.class, SettlementAccessDeniedException.class})
    protected ResponseEntity<ProblemDetail> handleForbidden(RuntimeException e, HttpServletRequest request) {
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
        pd.setProperty("pgErrorMessage", e.getErrorMessage());
        pd.setProperty("pgHttpStatus", e.getHttpStatus());
        pd.setProperty("pgRawBody", e.getRawBody());
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
                "동시 결제 처리 요청이 감지되었습니다. 결제 상태를 다시 조회해주세요.", request);
    }
}
