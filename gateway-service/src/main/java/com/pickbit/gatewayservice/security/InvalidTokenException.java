package com.pickbit.gatewayservice.security;

/**
 * 토큰이 없거나 서명/클레임이 유효하지 않을 때 발생합니다.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
