package com.pickbit.auctionservice.infrastructure.persistence;

import com.pickbit.auctionservice.domain.AuctionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuctionEventRepository extends JpaRepository<AuctionEvent, Long> {

    Page<AuctionEvent> findByAuctionIdOrderByIdDesc(Long auctionId, Pageable pageable);

    Page<AuctionEvent> findByAuctionIdAndIdGreaterThanOrderByIdAsc(Long auctionId, Long eventId, Pageable pageable);

    void deleteByAuctionId(Long auctionId);
}
