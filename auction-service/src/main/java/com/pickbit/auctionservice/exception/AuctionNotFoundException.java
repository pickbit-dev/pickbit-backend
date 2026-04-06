package com.pickbit.auctionservice.exception;

public class AuctionNotFoundException extends RuntimeException {

    public AuctionNotFoundException(Long auctionId) {
        super("경매를 찾을 수 없습니다. id=" + auctionId);
    }
}
