package com.grytonems.backend.domain.user;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSuspensionRepository extends JpaRepository<UserSuspension, Long> {

    /** 가장 최근 정지 (사용자가 정지 중이면 이게 지금 걸려 있는 정지) */
    Optional<UserSuspension> findFirstByUserIdOrderByIdDesc(Long userId);

    /** 정지 중인데 가장 최근 정지의 기간이 끝난 사용자 */
    @Query("""
            select s.user.id from UserSuspension s
            where s.user.status = :suspended
              and s.endsAt is not null and s.endsAt <= :now
              and s.id = (select max(s2.id) from UserSuspension s2 where s2.user = s.user)
            """)
    List<Long> findExpiredUserIds(@Param("suspended") UserStatus suspended, @Param("now") LocalDateTime now);
}
