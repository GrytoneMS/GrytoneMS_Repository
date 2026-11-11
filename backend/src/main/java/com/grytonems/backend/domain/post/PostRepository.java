package com.grytonems.backend.domain.post;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 모집글 저장·조회
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    /** POST-01: 팀장 1인당 모집중인 글 최대 3개 → 지금 몇 개인지 세기 (삭제된 글 제외) */
    long countByLeaderIdAndStatusAndDeletedAtIsNull(Long leaderId, RecruitStatus status);

    /** LIST-01·02 최신순: cursorId보다 id가 작은 글을 id 큰 순서로 (숨김·삭제 제외) */
    @Query("""
            SELECT p FROM Post p
            WHERE p.hidden = false
              AND p.deletedAt IS NULL
              AND p.id < :cursorId
            ORDER BY p.id DESC
            """)
    List<Post> findLatest(@Param("cursorId") Long cursorId, Pageable pageable);

    /** LIST-02 마감 임박순: 마감일 가까운 순, 같으면 id 작은 순 (숨김·삭제·마감일 지난 글 제외) */
    @Query("""
            SELECT p FROM Post p
            WHERE p.hidden = false
              AND p.deletedAt IS NULL
              AND p.deadline >= :today
              AND (p.deadline > :cursorDeadline
                   OR (p.deadline = :cursorDeadline AND p.id > :cursorId))
            ORDER BY p.deadline ASC, p.id ASC
            """)
    List<Post> findByDeadline(@Param("today") LocalDate today,
                              @Param("cursorDeadline") LocalDate cursorDeadline,
                              @Param("cursorId") Long cursorId,
                              Pageable pageable);
}