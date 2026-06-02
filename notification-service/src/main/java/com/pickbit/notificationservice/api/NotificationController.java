package com.pickbit.notificationservice.api;

import com.pickbit.library.auth.AuthContextHolder;
import com.pickbit.library.dto.PageResponse;
import com.pickbit.library.dto.PageableRequest;
import com.pickbit.notificationservice.api.dto.response.NotificationResponse;
import com.pickbit.notificationservice.application.NotificationCommandService;
import com.pickbit.notificationservice.application.NotificationQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationCommandService notificationCommandService;

    @GetMapping("/me")
    public ResponseEntity<PageResponse<NotificationResponse>> getMyNotifications(
            @ModelAttribute PageableRequest pageableRequest
    ) {
        return ResponseEntity.ok(PageResponse.from(notificationQueryService.getMyNotifications(
                AuthContextHolder.getUserId(), pageableRequest.toPageable(20))));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationCommandService.markRead(
                AuthContextHolder.getUserId(), notificationId));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationCommandService.markAllRead(AuthContextHolder.getUserId());
        return ResponseEntity.noContent().build();
    }
}
