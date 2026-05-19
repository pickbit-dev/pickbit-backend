package com.pickbit.paymentservice.exception;

public class PgUnavailableException extends RuntimeException {

    public PgUnavailableException(String message) {
        super(message);
    }
}
