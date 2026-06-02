package com.pickbit.notificationservice.application;

import com.pickbit.notificationservice.api.dto.response.NotificationResponse;
import com.pickbit.notificationservice.domain.Notification;
import com.pickbit.notificationservice.domain.enums.NotificationTargetType;
import com.pickbit.notificationservice.domain.enums.NotificationType;
import com.pickbit.notificationservice.exception.NotificationNotFoundException;
import com.pickbit.notificationservice.infrastructure.persistence.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationCommandService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification create(
            Long recipientUserId,
            NotificationType type,
            String title,
            String message,
            NotificationTargetType targetType,
            Long targetId
    ) {
        Notification notification = Notification.builder()
                .recipientUserId(recipientUserId)
                .type(type)
                .title(title)
                .message(message)
                .targetType(targetType)
                .targetId(targetId)
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional
    public NotificationResponse markRead(Long recipientUserId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, recipientUserId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        notification.markRead(LocalDateTime.now());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(Long recipientUserId) {
        notificationRepository.markAllRead(recipientUserId, LocalDateTime.now());
    }
}
