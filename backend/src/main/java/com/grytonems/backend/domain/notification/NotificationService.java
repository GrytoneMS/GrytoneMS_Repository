package com.grytonems.backend.domain.notification;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.notification.dto.NotificationResponse;
import com.grytonems.backend.domain.notification.dto.UnreadCountResponse;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 알림 (NOTI-01 알림 생성, NOTI-02 알림함).
 *
 * 다른 기능에서 알림을 보낼 때:
 * <pre>
 * notificationService.notify(받는사람Id, NotificationType.ACCEPTED, NotificationTargetType.POST, postId, "문구");
 * </pre>
 * 호출한 쪽의 트랜잭션에 함께 묶이므로, 그 작업이 실패해 취소되면 알림도 같이 취소됩니다.
 * 앱 푸시는 1차 범위에서 제외해 알림함에만 쌓습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    /** 알림 보관 기간 (NOTI-02) */
    public static final int RETENTION_DAYS = 30;
    private static final int MAX_MESSAGE_LENGTH = 200;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /** 알림 한 건 생성 */
    @Transactional
    public void notify(Long userId, NotificationType type, NotificationTargetType targetType, Long targetId,
            String message) {
        notificationRepository.save(build(userId, type, targetType, targetId, message));
    }

    /** 여러 사람에게 같은 알림 생성 */
    @Transactional
    public void notifyAll(Collection<Long> userIds, NotificationType type, NotificationTargetType targetType,
            Long targetId, String message) {
        List<Notification> notifications = userIds.stream()
                .distinct()
                .map(userId -> build(userId, type, targetType, targetId, message))
                .toList();
        notificationRepository.saveAll(notifications);
    }

    /** NOTI-02 알림함: 최근 30일, 최신순, 커서 방식 */
    public CursorPage<NotificationResponse> getMyNotifications(Long userId, Long cursor, Integer size) {
        int pageSize = CursorPage.normalizeSize(size);
        List<Notification> fetched = notificationRepository.findPage(userId, retentionStart(), cursor,
                PageRequest.of(0, pageSize + 1));
        return CursorPage.of(fetched, pageSize, Notification::getId, NotificationResponse::from);
    }

    /** 안 읽은 알림 개수 (종 아이콘 배지) */
    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countUnread(userId, retentionStart()));
    }

    /** 알림 하나 읽음 처리. 남의 알림이면 없는 알림으로 취급 */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없어요"));
        notification.markRead();
    }

    /** 모두 읽음 처리 */
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    private Notification build(Long userId, NotificationType type, NotificationTargetType targetType,
            Long targetId, String message) {
        String text = message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
        return Notification.builder()
                .user(userRepository.getReferenceById(userId))
                .type(type)
                .targetType(targetType)
                .targetId(targetId)
                .message(text)
                .build();
    }

    private LocalDateTime retentionStart() {
        return LocalDateTime.now().minusDays(RETENTION_DAYS);
    }
}
