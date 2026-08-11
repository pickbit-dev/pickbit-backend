package com.pickbit.library.exception;

/**
 * 인증은 되었으나 요청한 작업을 수행할 권한이 없을 때 발생합니다.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
