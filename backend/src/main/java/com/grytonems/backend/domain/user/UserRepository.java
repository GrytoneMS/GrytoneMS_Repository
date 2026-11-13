package com.grytonems.backend.domain.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    /** 다른 계정이 이미 인증에 쓴 학교 이메일인지 (AUTH-06: 학교 이메일 1개 = 계정 1개) */
    boolean existsBySchoolEmailAndIdNot(String schoolEmail, Long id);

    /** 목록 카드의 팀장 정보용: 학교까지 한 번에 (학교 이름을 꺼낼 때 추가 쿼리가 나가지 않게) */
    @Query("select u from User u left join fetch u.school where u.id in :ids")
    List<User> findAllWithSchoolByIdIn(@Param("ids") Collection<Long> ids);
}
