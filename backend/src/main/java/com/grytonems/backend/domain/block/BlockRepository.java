package com.grytonems.backend.domain.block;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Block b where b.blocker.id = :blockerId and b.blocked.id = :blockedId")
    int deleteByBlockerIdAndBlockedId(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /** 내가 차단한 사람 id */
    @Query("select b.blocked.id from Block b where b.blocker.id = :userId")
    List<Long> findBlockedIds(@Param("userId") Long userId);

    /** 나를 차단한 사람 id */
    @Query("select b.blocker.id from Block b where b.blocked.id = :userId")
    List<Long> findBlockerIds(@Param("userId") Long userId);

    /** 내 차단 목록: 최근 차단순, 커서는 직전 페이지 마지막 차단 id */
    @Query("""
            select b from Block b
            join fetch b.blocked u
            left join fetch u.school
            where b.blocker.id = :userId and (:cursor is null or b.id < :cursor)
            order by b.id desc
            """)
    List<Block> findMine(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);
}
