package com.pickbit.paymentservice.infrastructure.persistence;

import com.pickbit.paymentservice.domain.Inbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxRepository extends JpaRepository<Inbox, Long> {

    boolean existsByEventId(String eventId);
}
