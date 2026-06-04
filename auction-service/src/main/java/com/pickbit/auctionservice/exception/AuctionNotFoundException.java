package com.pickbit.auctionservice.exception;

public class AuctionNotFoundException extends RuntimeException {

    public AuctionNotFoundException(Long auctionId) {
        super("경매를 찾을 수 없습니다. id=" + auctionId);
    }

    public static AuctionNotFoundException byProductId(Long productId) {
        return new AuctionNotFoundException("상품에 연결된 경매를 찾을 수 없습니다. productId=" + productId);
    }

    private AuctionNotFoundException(String message) {
        super(message);
    }
}
