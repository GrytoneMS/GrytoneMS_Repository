package com.grytonems.backend.domain.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 모집글 저장·조회
 * 목록(LIST-01~04)은 조건이 그때그때 달라서 Specification(PostSpecs)으로 조회합니다.
 */
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /** POST-09: 팀장 1인당 모집중인 글 최대 3개 → 지금 몇 개인지 세기 (숨김·삭제된 글 제외) */
    long countByLeaderIdAndStatusAndHiddenFalseAndDeletedAtIsNull(Long leaderId, RecruitStatus status);}