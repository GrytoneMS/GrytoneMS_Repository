package com.grytonems.backend.domain.post;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 모집글 저장·조회
 * 목록(LIST-01~04)은 조건이 그때그때 달라서 Specification(PostSpecs)으로 조회합니다.
 */
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /** POST-09: 팀장 1인당 모집중인 글 최대 3개 → 지금 몇 개인지 세기 (숨김·삭제된 글 제외) */
    long countByLeaderIdAndStatusAndHiddenFalseAndDeletedAtIsNull(Long leaderId, RecruitStatus status);

    /** POST-06: 마감일이 지났는데 아직 모집중인 글 id (삭제된 글 제외, 숨김 글은 포함해 상태를 맞춤) */
    @Query("""
            select p.id from Post p
            where p.status = :status and p.deadline < :today and p.deletedAt is null
            order by p.id
            """)
    List<Long> findExpiredIds(@Param("status") RecruitStatus status, @Param("today") LocalDate today);

    /** NOTI-01 마감 D-1: 마감일이 특정 날짜인 모집중 글 (숨김·삭제된 글 제외) */
    List<Post> findAllByStatusAndDeadlineAndHiddenFalseAndDeletedAtIsNull(RecruitStatus status, LocalDate deadline);

    /** APPL-11 내 모집글: 최신순, 커서는 직전 페이지 마지막 글 id (숨김·삭제된 글 제외) */
    @Query("""
            select p from Post p
            where p.leaderId = :leaderId and p.deletedAt is null and p.hidden = false
              and (:status is null or p.status = :status)
              and (:cursor is null or p.id < :cursor)
            order by p.id desc
            """)
    List<Post> findMine(@Param("leaderId") Long leaderId, @Param("status") RecruitStatus status,
            @Param("cursor") Long cursor, Pageable pageable);
}
