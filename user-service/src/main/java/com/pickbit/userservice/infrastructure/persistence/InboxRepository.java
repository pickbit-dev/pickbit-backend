package com.pickbit.userservice.infrastructure.persistence;

import com.pickbit.userservice.domain.Inbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxRepository extends JpaRepository<Inbox, Long> {

    boolean existsByEventId(String eventId);
}
