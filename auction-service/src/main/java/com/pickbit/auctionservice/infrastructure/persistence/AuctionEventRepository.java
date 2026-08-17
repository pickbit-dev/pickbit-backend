package com.pickbit.auctionservice.infrastructure.persistence;

import com.pickbit.auctionservice.domain.AuctionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuctionEventRepository extends JpaRepository<AuctionEvent, Long> {

    Page<AuctionEvent> findByAuctionIdOrderBySequenceDesc(Long auctionId, Pageable pageable);

    Page<AuctionEvent> findByAuctionIdAndSequenceGreaterThanOrderBySequenceAsc(
            Long auctionId, Long sequence, Pageable pageable);

    /** Redis 상태를 DB에서 복구할 때 이어갈 순번을 알아내는 데 사용한다. */
    Optional<AuctionEvent> findTopByAuctionIdOrderBySequenceDesc(Long auctionId);

    long countByAuctionId(Long auctionId);

    void deleteByAuctionId(Long auctionId);

    /**
     * 오래된 이벤트를 청크 단위로 삭제합니다.
     * 한 번에 지우면 binlog 가 폭증하므로 나눠서 지웁니다.
     */
    @Modifying
    @Query(value = "DELETE FROM auction_event WHERE created_date < :threshold LIMIT :chunkSize",
            nativeQuery = true)
    int deleteOlderThan(@Param("threshold") LocalDateTime threshold, @Param("chunkSize") int chunkSize);
}
