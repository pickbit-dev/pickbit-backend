package com.pickbit.notificationservice.application;

import com.pickbit.notificationservice.api.dto.response.NotificationResponse;
import com.pickbit.notificationservice.infrastructure.persistence.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Long recipientUserId, Pageable pageable) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedDateDesc(recipientUserId, pageable)
                .map(NotificationResponse::from);
    }
}
