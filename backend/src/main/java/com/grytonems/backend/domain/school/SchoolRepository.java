package com.grytonems.backend.domain.school;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * [2] Repository: DB 조회·저장 담당.
 * 메서드 이름만 규칙대로 지으면 Spring Data JPA가 SQL을 만들어 줍니다.
 */
public interface SchoolRepository extends JpaRepository<School, Long> {

    List<School> findAllByActiveTrueOrderByNameAsc();

    boolean existsByEmailDomain(String emailDomain);
}
