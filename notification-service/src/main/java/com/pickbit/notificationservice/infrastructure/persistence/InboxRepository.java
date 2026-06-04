package com.pickbit.notificationservice.infrastructure.persistence;

import com.pickbit.notificationservice.domain.Inbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxRepository extends JpaRepository<Inbox, Long> {

    boolean existsBySuccessEventId(String successEventId);
}
