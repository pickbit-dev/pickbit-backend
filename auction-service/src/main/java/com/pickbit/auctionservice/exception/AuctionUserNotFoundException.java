package com.pickbit.auctionservice.exception;

public class AuctionUserNotFoundException extends RuntimeException {

    public AuctionUserNotFoundException(String nickname) {
        super("사용자를 찾을 수 없습니다. nickname=" + nickname);
    }
}
