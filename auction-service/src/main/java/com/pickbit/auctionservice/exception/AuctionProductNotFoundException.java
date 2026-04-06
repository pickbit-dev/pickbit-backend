package com.pickbit.auctionservice.exception;

public class AuctionProductNotFoundException extends RuntimeException {

    public AuctionProductNotFoundException(Long productId) {
        super("경매 대상 상품을 찾을 수 없습니다. productId=" + productId);
    }
}
