package com.grytonems.backend.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 정해진 시각에 도는 작업(@Scheduled)을 켭니다. 예: 모집글 자동 마감, 마감 D-1 알림.
 * 테스트에서는 application-test.yml 의 app.scheduling.enabled=false 로 끕니다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
