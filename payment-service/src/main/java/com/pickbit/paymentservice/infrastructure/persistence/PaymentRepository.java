package com.pickbit.paymentservice.infrastructure.persistence;

import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByAuctionId(Long auctionId);

    Optional<Payment> findByPgOrderId(String pgOrderId);

    Optional<Payment> findByPgPaymentKey(String pgPaymentKey);

    List<Payment> findByStatusAndPaymentDeadlineAtBefore(PaymentStatus status, LocalDateTime threshold);

    List<Payment> findByStatusAndConfirmDeadlineAtBefore(PaymentStatus status, LocalDateTime threshold);
}
