package com.grytonems.backend.domain.verification;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 인증 코드 임시 저장소. 실제로는 Redis(RedisVerificationCodeStore), 테스트에서는 메모리 구현을 씁니다.
 * 사용자 한 명당 대기 중인 코드는 하나만 둡니다 (새로 받으면 이전 코드는 무효).
 */
public interface VerificationCodeStore {

    void save(Long userId, PendingVerification pending, Duration ttl);

    /** 만료됐거나 없으면 빈 값 */
    Optional<PendingVerification> find(Long userId);

    /** 틀린 횟수를 1 올리고 올린 값을 돌려줍니다 */
    int increaseFailCount(Long userId);

    void delete(Long userId);

    /** 오늘(date) 코드를 받은 횟수 */
    int getSendCount(Long userId, LocalDate date);

    void increaseSendCount(Long userId, LocalDate date);
}
