package com.pickbit.paymentservice.infrastructure.persistence;

import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByAuctionId(Long auctionId);

    Optional<Payment> findByPgOrderId(String pgOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.pgOrderId = :pgOrderId")
    Optional<Payment> findByPgOrderIdForUpdate(@Param("pgOrderId") String pgOrderId);

    Optional<Payment> findByPgPaymentKey(String pgPaymentKey);

    List<Payment> findByStatusAndPaymentDeadlineAtBefore(PaymentStatus status, LocalDateTime threshold);

    List<Payment> findByStatusAndConfirmDeadlineAtBefore(PaymentStatus status, LocalDateTime threshold);
}
