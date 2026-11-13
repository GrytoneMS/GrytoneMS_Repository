package com.grytonems.backend.domain.notification;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.notification.dto.NotificationResponse;
import com.grytonems.backend.domain.notification.dto.NotificationSettingDto;
import com.grytonems.backend.domain.notification.dto.UnreadCountResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 알림함 API (NOTI-02, 로그인 필요) */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSettingService notificationSettingService;

    /** 알림 목록 (최근 30일, 최신순) */
    @GetMapping("/api/notifications")
    public ApiResponse<CursorPage<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal AuthUser loginUser,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(notificationService.getMyNotifications(loginUser.id(), cursor, size));
    }

    /** 안 읽은 알림 개수 */
    @GetMapping("/api/notifications/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(@AuthenticationPrincipal AuthUser loginUser) {
        return ApiResponse.ok(notificationService.getUnreadCount(loginUser.id()));
    }

    /** 모두 읽음 */
    @PatchMapping("/api/notifications/read-all")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal AuthUser loginUser) {
        notificationService.markAllRead(loginUser.id());
        return ApiResponse.ok();
    }

    /** 알림 하나 읽음 */
    @PatchMapping("/api/notifications/{notificationId}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long notificationId) {
        notificationService.markRead(loginUser.id(), notificationId);
        return ApiResponse.ok();
    }

    /** NOTI-03 알림 설정 조회 */
    @GetMapping("/api/users/me/notification-settings")
    public ApiResponse<NotificationSettingDto> getSettings(@AuthenticationPrincipal AuthUser loginUser) {
        return ApiResponse.ok(notificationSettingService.get(loginUser.id()));
    }

    /** NOTI-03 알림 설정 저장 (세 값 모두) */
    @PutMapping("/api/users/me/notification-settings")
    public ApiResponse<NotificationSettingDto> updateSettings(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody NotificationSettingDto request) {
        return ApiResponse.ok(notificationSettingService.update(loginUser.id(), request));
    }
}
