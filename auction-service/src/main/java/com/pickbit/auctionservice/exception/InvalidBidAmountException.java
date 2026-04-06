package com.pickbit.auctionservice.exception;

public class InvalidBidAmountException extends RuntimeException {

    public InvalidBidAmountException(String message) {
        super(message);
    }
}
