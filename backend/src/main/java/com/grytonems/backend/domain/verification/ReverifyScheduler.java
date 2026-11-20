package com.grytonems.backend.domain.verification;

import java.time.LocalDate;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 학기 재인증 (AUTH-08), 한국 시간.
 * - 재인증 요청 알림: 매일 오전 9시에 확인해 3월 1일·9월 1일에만 발송
 * - 기한 경과: 매일 자정(00:00:10) + 서버 시작 직후. 기한(3/14·9/14)이 지난 날부터 미인증으로 바꿈
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReverifyScheduler {

    private static final String ZONE = "Asia/Seoul";

    private final ReverifyService reverifyService;

    @Scheduled(cron = "0 0 9 * * *", zone = ZONE)
    public void notifyReverify() {
        int sent = reverifyService.notifyReverify(LocalDate.now());
        if (sent > 0) {
            log.info("[학기 재인증] 요청 알림 {}건 발송", sent);
        }
    }

    @Scheduled(cron = "10 0 0 * * *", zone = ZONE)
    public void expire() {
        int expired = reverifyService.expire(LocalDate.now());
        if (expired > 0) {
            log.info("[학기 재인증] 기한 지난 회원 {}명 미인증 처리", expired);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void expireOnStartup() {
        expire();
    }
}
