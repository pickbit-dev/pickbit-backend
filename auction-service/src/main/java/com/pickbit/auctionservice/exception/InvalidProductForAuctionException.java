package com.pickbit.auctionservice.exception;

public class InvalidProductForAuctionException extends RuntimeException {

    public InvalidProductForAuctionException(String message) {
        super(message);
    }
}
