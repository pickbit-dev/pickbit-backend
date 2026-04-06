package com.pickbit.auctionservice.exception;

public class InvalidAuctionStatusException extends RuntimeException {

    public InvalidAuctionStatusException(String message) {
        super(message);
    }
}
