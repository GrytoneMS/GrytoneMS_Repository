package com.grytonems.backend.domain.application;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.post.RecruitStatus;

/**
 * 수락할 때 역할의 자리(filled_count)를 안전하게 채우는 쿼리 모음 (APPL-05, POST-05·06).
 *
 * DB 설계서는 "post_roles 행을 SELECT … FOR UPDATE로 잠그고 filled_count < headcount일 때만 수락"이라고 적었는데,
 * 여기서는 같은 효과를 "조건을 건 UPDATE 한 번"으로 냅니다.
 * UPDATE가 행을 잠그고 조건을 다시 확인하므로, 동시에 여러 명을 수락해도 모집 인원을 넘지 않습니다.
 */
public interface RoleSeatRepository extends Repository<PostRole, Long> {

    /** 자리가 남아 있고 모집중이면 filled_count + 1. 채웠으면 1, 자리가 없으면 0 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PostRole r
            set r.filledCount = r.filledCount + 1
            where r.id = :id and r.status = :open and r.filledCount < r.headcount
            """)
    int fillOneSeat(@Param("id") Long id, @Param("open") RecruitStatus open);

    /** 인원이 다 찼으면 역할 마감. 마감했으면 1 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PostRole r
            set r.status = :closed
            where r.id = :id and r.status = :open and r.filledCount >= r.headcount
            """)
    int closeIfFull(@Param("id") Long id, @Param("open") RecruitStatus open, @Param("closed") RecruitStatus closed);

    /** 글에 아직 모집중인 역할이 몇 개인지 */
    @Query("select count(r) from PostRole r where r.post.id = :postId and r.status = :open")
    long countOpenRoles(@Param("postId") Long postId, @Param("open") RecruitStatus open);
}
