package com.pickbit.paymentservice.infrastructure.persistence;

import com.pickbit.paymentservice.domain.PgWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PgWebhookLogRepository extends JpaRepository<PgWebhookLog, Long> {

    boolean existsByPgEventId(String pgEventId);

    Optional<PgWebhookLog> findByPgEventId(String pgEventId);
}
