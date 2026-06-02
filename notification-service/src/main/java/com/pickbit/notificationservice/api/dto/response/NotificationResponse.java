package com.pickbit.notificationservice.api.dto.response;

import com.pickbit.notificationservice.domain.Notification;
import com.pickbit.notificationservice.domain.enums.NotificationTargetType;
import com.pickbit.notificationservice.domain.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String title,
        String message,
        boolean read,
        NotificationTargetType targetType,
        Long targetId,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getReadAt(),
                notification.getCreatedDate()
        );
    }
}
