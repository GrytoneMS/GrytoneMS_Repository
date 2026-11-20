package com.grytonems.backend.domain.notification;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NOTI-02 알림 30일 보관: 매일 새벽 3시(한국 시간)에 30일 지난 알림을 지웁니다 (DB 설계서 배치 작업).
 * 알림함 조회는 원래도 30일 안의 알림만 보여주므로, 이 작업은 표가 계속 커지지 않게 정리만 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void cleanUp() {
        int deleted = notificationService.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("[알림 정리] 30일 지난 알림 {}건 삭제", deleted);
        }
    }
}
