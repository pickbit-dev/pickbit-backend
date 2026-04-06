package com.pickbit.auctionservice.infrastructure.persistence;

import com.pickbit.auctionservice.domain.Bid;
import com.pickbit.auctionservice.domain.enums.BidStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    Page<Bid> findByAuctionIdOrderByAmountDesc(Long auctionId, Pageable pageable);

    Optional<Bid> findTopByAuctionIdOrderByAmountDesc(Long auctionId);

    List<Bid> findByAuctionId(Long auctionId);

    @Modifying
    @Query("UPDATE Bid b SET b.bidStatus = :status WHERE b.auction.id = :auctionId AND b.bidStatus = 'ACTIVE'")
    void updateAllActiveBidsByAuctionId(@Param("auctionId") Long auctionId, @Param("status") BidStatus status);
}
