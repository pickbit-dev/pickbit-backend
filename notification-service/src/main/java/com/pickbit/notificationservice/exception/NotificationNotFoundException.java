package com.pickbit.notificationservice.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long notificationId) {
        super("알림을 찾을 수 없습니다. notificationId=" + notificationId);
    }
}
