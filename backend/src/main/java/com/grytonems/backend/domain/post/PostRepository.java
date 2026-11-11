package com.grytonems.backend.domain.post;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 모집글 저장·조회
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    /** POST-01: 팀장 1인당 모집중인 글 최대 3개 → 지금 몇 개인지 세기 (삭제된 글 제외) */
    long countByLeaderIdAndStatusAndDeletedAtIsNull(Long leaderId, RecruitStatus status);
}