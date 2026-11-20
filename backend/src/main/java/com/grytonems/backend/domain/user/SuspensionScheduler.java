package com.grytonems.backend.domain.user;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ADMN-04 정지 기간 종료: 매시 정각(10초)에 기간이 끝난 정지를 풉니다 (한국 시간).
 * 테스트에서는 app.scheduling.enabled=false 라 자동으로 돌지 않고, liftExpired(now)를 직접 호출합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuspensionScheduler {

    private final SuspensionService suspensionService;

    @Scheduled(cron = "10 0 * * * *", zone = "Asia/Seoul")
    public void liftExpired() {
        liftExpired(LocalDateTime.now());
    }

    /** 한 명씩 따로 처리해서, 한 명에서 오류가 나도 나머지는 계속 풉니다 */
    public int liftExpired(LocalDateTime now) {
        int lifted = 0;
        for (Long userId : suspensionService.findExpiredUserIds(now)) {
            try {
                if (suspensionService.liftIfExpired(userId, now)) {
                    lifted++;
                }
            } catch (RuntimeException e) {
                log.error("[정지 종료] 사용자 {} 해제 실패", userId, e);
            }
        }
        if (lifted > 0) {
            log.info("[정지 종료] {}명 해제", lifted);
        }
        return lifted;
    }
}
