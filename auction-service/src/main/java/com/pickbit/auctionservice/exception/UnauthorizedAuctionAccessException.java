package com.pickbit.auctionservice.exception;

public class UnauthorizedAuctionAccessException extends RuntimeException {

    public UnauthorizedAuctionAccessException() {
        super("해당 경매에 대한 권한이 없습니다.");
    }
}
