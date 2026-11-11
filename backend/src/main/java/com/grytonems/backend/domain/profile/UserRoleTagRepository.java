package com.grytonems.backend.domain.profile;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleTagRepository extends JpaRepository<UserRoleTag, Long> {

    List<UserRoleTag> findAllByUserIdOrderByIdAsc(Long userId);

    /** 태그를 통째로 바꿀 때 먼저 기존 태그를 지웁니다 (바로 실행돼야 새 태그와 UQ가 충돌하지 않음) */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserRoleTag t where t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
