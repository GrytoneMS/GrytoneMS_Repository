package com.grytonems.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * created_at, updated_at 자동 기록을 켭니다 (BaseTimeEntity).
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
