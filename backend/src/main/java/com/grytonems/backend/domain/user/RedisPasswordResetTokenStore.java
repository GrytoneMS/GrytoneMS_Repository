package com.grytonems.backend.domain.user;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** password-reset:{token} → userId, 30분 뒤 자동 삭제 */
@Component
public class RedisPasswordResetTokenStore implements PasswordResetTokenStore {

    private static final String KEY = "password-reset:%s";

    private final StringRedisTemplate redis;

    public RedisPasswordResetTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String token, Long userId, Duration ttl) {
        redis.opsForValue().set(KEY.formatted(token), String.valueOf(userId), ttl);
    }

    @Override
    public Optional<Long> consume(String token) {
        String value = redis.opsForValue().getAndDelete(KEY.formatted(token));
        return Optional.ofNullable(value).map(Long::valueOf);
    }
}
