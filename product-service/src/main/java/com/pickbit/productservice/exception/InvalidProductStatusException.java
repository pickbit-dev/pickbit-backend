package com.pickbit.productservice.exception;

public class InvalidProductStatusException extends RuntimeException {

    public InvalidProductStatusException(String message) {
        super(message);
    }
}
