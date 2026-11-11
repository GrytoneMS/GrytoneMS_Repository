package com.grytonems.backend.domain.post;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공모전·대회 행사 정보 저장·조회
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    /** 글 하나의 행사 정보 (없을 수도 있음) */
    Optional<Event> findByPostId(Long postId);
}