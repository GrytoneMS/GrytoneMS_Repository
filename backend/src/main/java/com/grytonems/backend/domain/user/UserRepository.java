package com.grytonems.backend.domain.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    /** 다른 계정이 이미 인증에 쓴 학교 이메일인지 (AUTH-06: 학교 이메일 1개 = 계정 1개) */
    boolean existsBySchoolEmailAndIdNot(String schoolEmail, Long id);
}
