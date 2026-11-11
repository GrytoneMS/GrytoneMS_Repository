package com.grytonems.backend.domain.notification.dto;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.notification.Notification;
import com.grytonems.backend.domain.notification.NotificationTargetType;
import com.grytonems.backend.domain.notification.NotificationType;

/**
 * 알림 하나.
 * targetType·targetId: 눌렀을 때 이동할 곳 (예: POST, 12 → 모집글 12 상세)
 */
public record NotificationResponse(
        Long id,
        NotificationType type,
        NotificationTargetType targetType,
        Long targetId,
        String message,
        boolean read,
        LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(),
                notification.getTargetType(), notification.getTargetId(), notification.getMessage(),
                notification.isRead(), notification.getCreatedAt());
    }
}
