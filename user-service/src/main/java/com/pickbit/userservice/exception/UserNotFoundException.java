package com.pickbit.userservice.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long accountId) {
        super("사용자를 찾을 수 없습니다. accountId=" + accountId);
    }
}
