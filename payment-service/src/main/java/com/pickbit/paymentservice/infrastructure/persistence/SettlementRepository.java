package com.pickbit.paymentservice.infrastructure.persistence;

import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByPaymentId(Long paymentId);

    Page<Settlement> findBySellerUserIdOrderByIdDesc(Long sellerUserId, Pageable pageable);

    Page<Settlement> findBySellerUserIdAndStatusOrderByIdDesc(
            Long sellerUserId, SettlementStatus status, Pageable pageable);

    /**
     * 판매자의 상태별 정산 합계입니다.
     *
     * @return {@code [status, 건수, 정산액 합계]} 목록
     */
    @Query("""
            SELECT s.status, COUNT(s), COALESCE(SUM(s.netSellerAmount), 0)
            FROM Settlement s
            WHERE s.sellerUserId = :sellerUserId
            GROUP BY s.status
            """)
    java.util.List<Object[]> summarizeBySeller(@Param("sellerUserId") Long sellerUserId);

    /** 진단용. 재시도 상한을 넘겨 방치된 정산 건수. */
    @Query("""
            SELECT COUNT(s) FROM Settlement s
            WHERE s.status = com.pickbit.paymentservice.domain.enums.SettlementStatus.FAILED
              AND s.retryCount >= :maxRetries
            """)
    long countAbandoned(@Param("maxRetries") int maxRetries);

    /** 진단용. 아직 정산되지 않은 총액. */
    @Query("""
            SELECT COALESCE(SUM(s.netSellerAmount), 0) FROM Settlement s
            WHERE s.status <> com.pickbit.paymentservice.domain.enums.SettlementStatus.COMPLETED
            """)
    BigDecimal sumOutstanding();
}
