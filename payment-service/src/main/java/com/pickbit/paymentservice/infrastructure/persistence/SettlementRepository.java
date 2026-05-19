package com.pickbit.paymentservice.infrastructure.persistence;

import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByPaymentId(Long paymentId);

    List<Settlement> findByStatus(SettlementStatus status);
}
