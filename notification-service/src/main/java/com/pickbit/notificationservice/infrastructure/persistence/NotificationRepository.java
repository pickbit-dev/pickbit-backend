package com.pickbit.notificationservice.infrastructure.persistence;

import com.pickbit.notificationservice.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserIdOrderByCreatedDateDesc(Long recipientUserId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    @Modifying
    @Query("update Notification n set n.readAt = :readAt where n.recipientUserId = :recipientUserId and n.readAt is null")
    int markAllRead(@Param("recipientUserId") Long recipientUserId, @Param("readAt") LocalDateTime readAt);
}
