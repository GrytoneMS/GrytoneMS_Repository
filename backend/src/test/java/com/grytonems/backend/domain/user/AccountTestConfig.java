package com.grytonems.backend.domain.user;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 비밀번호 재설정 테스트용: Redis 대신 메모리에 토큰 저장 */
@TestConfiguration
public class AccountTestConfig {

    @Bean
    @Primary
    InMemoryResetTokenStore inMemoryResetTokenStore() {
        return new InMemoryResetTokenStore();
    }

    public static class InMemoryResetTokenStore implements PasswordResetTokenStore {

        private final Map<String, Long> tokens = new ConcurrentHashMap<>();

        @Override
        public void save(String token, Long userId, Duration ttl) {
            tokens.put(token, userId);
        }

        @Override
        public Optional<Long> consume(String token) {
            return Optional.ofNullable(tokens.remove(token));
        }

        public void clear() {
            tokens.clear();
        }
    }
}
