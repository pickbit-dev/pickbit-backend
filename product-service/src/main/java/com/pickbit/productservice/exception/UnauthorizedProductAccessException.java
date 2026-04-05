package com.pickbit.productservice.exception;

public class UnauthorizedProductAccessException extends RuntimeException {

    public UnauthorizedProductAccessException() {
        super("해당 상품에 대한 권한이 없습니다.");
    }
}
