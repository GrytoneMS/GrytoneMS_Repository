package com.grytonems.backend.domain.post;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 마감일 기준 배치 실행 시각 (한국 시간).
 * - 자동 마감: 매시 정각 + 서버 시작 직후. 자정이 지나면 바로 마감되고, 서버가 꺼져 있던 동안 놓친 글도 따라잡습니다.
 * - 마감 D-1 알림: 매일 오전 9시 (밤 10시~아침 8시 방해 금지 시간을 피함, NOTI-03)
 *
 * 테스트에서는 app.scheduling.enabled=false 라 자동으로 돌지 않고, PostDeadlineService를 직접 호출합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostDeadlineScheduler {

    private static final String ZONE = "Asia/Seoul";

    private final PostDeadlineService postDeadlineService;

    @Scheduled(cron = "5 0 * * * *", zone = ZONE)
    public void closeExpiredPosts() {
        closeExpiredPosts(LocalDate.now());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void closeExpiredPostsOnStartup() {
        closeExpiredPosts(LocalDate.now());
    }

    @Scheduled(cron = "0 0 9 * * *", zone = ZONE)
    public void notifyDeadlineTomorrow() {
        notifyDeadlineTomorrowFor(LocalDate.now());
    }

    public int notifyDeadlineTomorrowFor(LocalDate today) {
        int sent = postDeadlineService.notifyDeadlineTomorrow(today);
        log.info("[마감 D-1 알림] {}건 발송", sent);
        return sent;
    }

    /** 글마다 따로 마감해서, 한 글에서 오류가 나도 나머지는 계속 처리합니다 */
    public int closeExpiredPosts(LocalDate today) {
        List<Long> postIds = postDeadlineService.findExpiredPostIds(today);
        int closed = 0;
        for (Long postId : postIds) {
            try {
                if (postDeadlineService.closeExpired(postId, today)) {
                    closed++;
                }
            } catch (RuntimeException e) {
                log.error("[자동 마감] 모집글 {} 마감 실패", postId, e);
            }
        }
        if (closed > 0) {
            log.info("[자동 마감] 마감일 지난 모집글 {}건 마감", closed);
        }
        return closed;
    }
}
