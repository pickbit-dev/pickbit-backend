package com.pickbit.authservice.infrastructure.persistence;

import com.pickbit.authservice.domain.Inbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxRepository extends JpaRepository<Inbox, Long> {

    boolean existsBySuccessEventId(String successEventId);
}
