package com.grytonems.backend.domain.verification;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis에 인증 코드를 저장합니다.
 * - verify:school-email:{userId}            → hash(schoolId, schoolEmail, code, failCount), 5분 뒤 자동 삭제
 * - verify:school-email:count:{userId}:{날짜} → 그날 발송 횟수, 이틀 뒤 자동 삭제
 */
@Component
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String PENDING_KEY = "verify:school-email:%d";
    private static final String COUNT_KEY = "verify:school-email:count:%d:%s";
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redis;

    public RedisVerificationCodeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(Long userId, PendingVerification pending, Duration ttl) {
        String key = pendingKey(userId);
        redis.delete(key);
        redis.opsForHash().putAll(key, Map.of(
                "schoolId", String.valueOf(pending.schoolId()),
                "schoolEmail", pending.schoolEmail(),
                "code", pending.code(),
                "failCount", String.valueOf(pending.failCount())));
        redis.expire(key, ttl);
    }

    @Override
    public Optional<PendingVerification> find(Long userId) {
        Map<Object, Object> values = redis.opsForHash().entries(pendingKey(userId));
        if (values.isEmpty() || !values.containsKey("code")) {
            return Optional.empty();
        }
        return Optional.of(new PendingVerification(
                Long.valueOf((String) values.get("schoolId")),
                (String) values.get("schoolEmail"),
                (String) values.get("code"),
                Integer.parseInt((String) values.getOrDefault("failCount", "0"))));
    }

    @Override
    public int increaseFailCount(Long userId) {
        String key = pendingKey(userId);
        Long count = redis.opsForHash().increment(key, "failCount", 1);
        // 그 사이 만료됐다면 만료 시간 없는 키가 새로 생기므로 지웁니다
        Long ttl = redis.getExpire(key);
        if (ttl != null && ttl < 0) {
            redis.delete(key);
        }
        return count == null ? 0 : count.intValue();
    }

    @Override
    public void delete(Long userId) {
        redis.delete(pendingKey(userId));
    }

    @Override
    public int getSendCount(Long userId, LocalDate date) {
        String value = redis.opsForValue().get(countKey(userId, date));
        return value == null ? 0 : Integer.parseInt(value);
    }

    @Override
    public void increaseSendCount(Long userId, LocalDate date) {
        String key = countKey(userId, date);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofDays(2));
        }
    }

    private String pendingKey(Long userId) {
        return PENDING_KEY.formatted(userId);
    }

    private String countKey(Long userId, LocalDate date) {
        return COUNT_KEY.formatted(userId, date.format(DATE));
    }
}
