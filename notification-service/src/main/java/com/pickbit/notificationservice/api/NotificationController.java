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

/**
 * 알림 API 컨트롤러.
 *
 * <p>현재 로그인한 사용자의 알림 목록 조회와 읽음 처리 기능을 제공합니다.
 * 사용자 식별자는 Gateway가 전달한 인증 컨텍스트에서 조회합니다.
 */
@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;
    private final NotificationCommandService notificationCommandService;

    /**
     * 현재 로그인한 사용자의 알림 목록을 페이징 조회합니다.
     *
     * @param pageableRequest 페이징 및 정렬 조건
     * @return 알림 목록 (페이징)
     */
    @GetMapping("/me")
    public ResponseEntity<PageResponse<NotificationResponse>> getMyNotifications(
            @ModelAttribute PageableRequest pageableRequest
    ) {
        return ResponseEntity.ok(PageResponse.from(notificationQueryService.getMyNotifications(
                AuthContextHolder.getUserId(), pageableRequest.toPageable(20))));
    }

    /**
     * 현재 로그인한 사용자의 특정 알림을 읽음 처리합니다.
     *
     * <p>요청자는 본인에게 발송된 알림만 읽음 처리할 수 있습니다.
     * 이미 읽은 알림이면 기존 읽음 시각을 유지합니다.
     *
     * @param notificationId 읽음 처리할 알림 ID
     * @return 읽음 처리된 알림 정보
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationCommandService.markRead(
                AuthContextHolder.getUserId(), notificationId));
    }

    /**
     * 현재 로그인한 사용자의 모든 미읽음 알림을 읽음 처리합니다.
     *
     * @return 응답 본문 없음 (HTTP 204)
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationCommandService.markAllRead(AuthContextHolder.getUserId());
        return ResponseEntity.noContent().build();
    }
}
