package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.Bid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuctionCompleter {

    private static final String PRODUCT_STATUS = "AUCTION_COMPLETED";

    private final OutboxRecorder outboxRecorder;

    @Transactional(propagation = Propagation.REQUIRED)
    public void completeWithWinner(Auction auction, Bid winner, String productStatusReason) {
        winner.markWinning();
        auction.complete(winner.getBidderNickname(), winner.getAmount());
        auction.assignWinnerUserId(winner.getBidderUserId());

        outboxRecorder.auctionWonEvent(auction, winner);
        outboxRecorder.productStatusUpdateEvent(
                auction.getProductId(), PRODUCT_STATUS, productStatusReason, auction.getId());
    }
}
