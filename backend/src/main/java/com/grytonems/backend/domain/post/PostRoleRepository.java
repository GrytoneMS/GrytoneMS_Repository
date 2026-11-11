package com.grytonems.backend.domain.post;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 모집글 역할 저장·조회
 */
public interface PostRoleRepository extends JpaRepository<PostRole, Long> {

    /** 글 하나의 역할 목록 (등록한 순서대로) */
    List<PostRole> findAllByPostIdOrderByIdAsc(Long postId);

    /** LIST-01: 글 여러 개의 역할을 한 번에 (목록 카드용) */
    List<PostRole> findAllByPostIdInOrderByIdAsc(List<Long> postIds);
}