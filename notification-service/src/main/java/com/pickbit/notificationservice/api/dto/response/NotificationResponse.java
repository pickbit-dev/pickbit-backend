package com.pickbit.notificationservice.api.dto.response;

import com.pickbit.notificationservice.domain.Notification;
import com.pickbit.notificationservice.domain.enums.NotificationTargetType;
import com.pickbit.notificationservice.domain.enums.NotificationType;

import java.time.LocalDateTime;

/**
 * 알림 조회 응답입니다.
 *
 * @param notificationId 알림 ID
 * @param type 알림 타입
 * @param title 알림 제목
 * @param message 알림 본문
 * @param read 읽음 여부
 * @param targetType 알림 대상 타입
 * @param targetId 알림 대상 ID
 * @param readAt 읽은 시각
 * @param createdAt 알림 생성 시각
 */
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
