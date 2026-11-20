package com.grytonems.backend.domain.verification;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.grytonems.backend.global.mail.EmailSender;

/**
 * 학교 인증 테스트용 대역.
 * - Redis 대신 메모리에 코드 저장 (만료는 expire()로 흉내)
 * - 메일 대신 보낸 내용을 기록해 테스트에서 코드를 꺼내 씀
 */
@TestConfiguration
public class VerificationTestConfig {

    @Bean
    @Primary
    InMemoryCodeStore inMemoryCodeStore() {
        return new InMemoryCodeStore();
    }

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }

    public static class InMemoryCodeStore implements VerificationCodeStore {

        private final Map<Long, PendingVerification> pending = new ConcurrentHashMap<>();
        private final Map<String, Integer> sendCounts = new ConcurrentHashMap<>();

        @Override
        public void save(Long userId, PendingVerification value, Duration ttl) {
            pending.put(userId, value);
        }

        @Override
        public Optional<PendingVerification> find(Long userId) {
            return Optional.ofNullable(pending.get(userId));
        }

        @Override
        public int increaseFailCount(Long userId) {
            PendingVerification updated = pending.computeIfPresent(userId, (id, p) ->
                    new PendingVerification(p.schoolId(), p.schoolEmail(), p.code(), p.failCount() + 1));
            return updated == null ? 0 : updated.failCount();
        }

        @Override
        public void delete(Long userId) {
            pending.remove(userId);
        }

        @Override
        public int getSendCount(Long userId, LocalDate date) {
            return sendCounts.getOrDefault(userId + ":" + date, 0);
        }

        @Override
        public void increaseSendCount(Long userId, LocalDate date) {
            sendCounts.merge(userId + ":" + date, 1, Integer::sum);
        }

        /** 5분이 지난 것처럼 코드를 지웁니다 */
        public void expire(Long userId) {
            pending.remove(userId);
        }

        public void clear() {
            pending.clear();
            sendCounts.clear();
        }
    }

    public static class RecordingEmailSender implements EmailSender {

        public record Sent(String to, String subject, String body) {
        }

        private final List<Sent> sent = new ArrayList<>();

        @Override
        public synchronized void send(String to, String subject, String body) {
            sent.add(new Sent(to, subject, body));
        }

        public synchronized Sent last() {
            return sent.get(sent.size() - 1);
        }

        /** 마지막 메일 본문에서 6자리 코드를 꺼냅니다 */
        public synchronized String lastCode() {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{6}").matcher(last().body());
            if (!m.find()) {
                throw new IllegalStateException("메일에 코드가 없음: " + last().body());
            }
            return m.group();
        }

        public synchronized int count() {
            return sent.size();
        }

        public synchronized void clear() {
            sent.clear();
        }
    }
}
