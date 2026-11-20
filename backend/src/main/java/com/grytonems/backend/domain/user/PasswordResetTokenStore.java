package com.grytonems.backend.domain.user;

import java.time.Duration;
import java.util.Optional;

/** 비밀번호 재설정 토큰 저장소 (AUTH-05). 실제로는 Redis, 테스트에서는 메모리 구현을 씁니다 */
public interface PasswordResetTokenStore {

    void save(String token, Long userId, Duration ttl);

    /** 토큰을 꺼내면서 지웁니다(한 번만 사용). 없거나 만료됐으면 빈 값 */
    Optional<Long> consume(String token);
}
