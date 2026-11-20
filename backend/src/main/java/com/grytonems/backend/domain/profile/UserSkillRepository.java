package com.grytonems.backend.domain.profile;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findAllByUserIdOrderByIdAsc(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserSkill s where s.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
