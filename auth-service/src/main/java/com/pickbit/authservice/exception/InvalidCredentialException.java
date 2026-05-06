package com.pickbit.authservice.exception;

public class InvalidCredentialException extends RuntimeException {

    public InvalidCredentialException() {
        super("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
