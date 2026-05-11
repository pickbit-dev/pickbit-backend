package com.pickbit.productservice.infrastructure.persistence;

import com.pickbit.productservice.domain.Inbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxRepository extends JpaRepository<Inbox, Long> {

    boolean existsByEventId(String eventId);
}
