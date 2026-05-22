package com.pickbit.auctionservice.exception;

import com.pickbit.library.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuctionExceptionHandler extends GlobalExceptionHandler {

    @ExceptionHandler({
            AuctionNotFoundException.class,
            AuctionProductNotFoundException.class,
            AuctionUserNotFoundException.class
    })
    protected ResponseEntity<ProblemDetail> handleNotFound(RuntimeException e, HttpServletRequest request) {
        logException(HttpStatus.NOT_FOUND, e);
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler({
            InvalidBidAmountException.class,
            InvalidAuctionStatusException.class,
            InvalidProductForAuctionException.class
    })
    protected ResponseEntity<ProblemDetail> handleConflict(RuntimeException e, HttpServletRequest request) {
        logException(HttpStatus.CONFLICT, e);
        return buildResponse(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedAuctionAccessException.class)
    protected ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedAuctionAccessException e, HttpServletRequest request) {
        logException(HttpStatus.FORBIDDEN, e);
        return buildResponse(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    protected ResponseEntity<ProblemDetail> handleOptimisticLock(
            ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
        logException(HttpStatus.CONFLICT, e);
        return buildResponse(HttpStatus.CONFLICT,
                "동시 처리 충돌이 발생했습니다. 잠시 후 다시 시도해주세요.", request);
    }
}
