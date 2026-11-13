package com.grytonems.backend.domain.application;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** APPL-12 대기 지원 리마인드: 매일 오전 9시 (한국 시간, 방해 금지 시간 밖) */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationReminderScheduler {

    private final ApplicationReminderService reminderService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void remind() {
        int sent = reminderService.remind(LocalDateTime.now());
        if (sent > 0) {
            log.info("[대기 지원 리마인드] {}건 발송", sent);
        }
    }
}
