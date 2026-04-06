package com.pickbit.auctionservice.infrastructure.persistence;

import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Auction a WHERE a.id = :id")
    Optional<Auction> findByIdWithLock(@Param("id") Long id);

    Page<Auction> findByAuctionStatus(AuctionStatus auctionStatus, Pageable pageable);

    Page<Auction> findAll(Pageable pageable);

    boolean existsByProductIdAndAuctionStatusIn(Long productId, List<AuctionStatus> statuses);

    @Query("SELECT a FROM Auction a WHERE a.auctionStatus = 'ACTIVE' AND a.endTime <= :now")
    List<Auction> findExpiredActiveAuctions(@Param("now") LocalDateTime now);

    @Query("SELECT a FROM Auction a WHERE a.auctionStatus = 'SCHEDULED' AND a.startTime <= :now")
    List<Auction> findScheduledAuctionsToActivate(@Param("now") LocalDateTime now);
}
