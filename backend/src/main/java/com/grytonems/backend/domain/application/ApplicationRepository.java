package com.grytonems.backend.domain.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    /** APPL-01: 이 글에 지원한 적이 있는지 (취소·거절 포함 → 재지원 불가) */
    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);

    /** LIST-05: 상세 화면의 "내 지원" 표시용 (글 하나에 한 번만 지원 가능) */
    Optional<Application> findByPostIdAndApplicantId(Long postId, Long applicantId);

    /** APPL-07: 이 글에 특정 상태(수락)인 지원이 있는지 */
    boolean existsByPostIdAndApplicantIdAndStatus(Long postId, Long applicantId, ApplicationStatus status);

    /** 수락·거절 처리용: 글과 역할을 함께 읽습니다 */
    @Query("""
            select a from Application a
            join fetch a.post
            join fetch a.postRole
            where a.id = :id
            """)
    Optional<Application> findWithPostAndRoleById(@Param("id") Long id);

    /** APPL-04: 지원자 목록 (취소 제외, 지원 시간순). 지원자와 학교를 함께 읽습니다 */
    @Query("""
            select a from Application a
            join fetch a.applicant u
            left join fetch u.school
            where a.post.id = :postId and a.status <> :excluded
            order by a.createdAt asc, a.id asc
            """)
    List<Application> findApplicantsByPostId(@Param("postId") Long postId,
            @Param("excluded") ApplicationStatus excluded);

    /** APPL-10: 내 지원 내역 (최신순, 커서 방식). cursor는 직전 페이지 마지막 지원 id */
    @Query("""
            select a from Application a
            join fetch a.post
            join fetch a.postRole
            where a.applicant.id = :applicantId
              and (:status is null or a.status = :status)
              and (:cursor is null or a.id < :cursor)
            order by a.id desc
            """)
    List<Application> findMine(@Param("applicantId") Long applicantId,
            @Param("status") ApplicationStatus status,
            @Param("cursor") Long cursor,
            Pageable pageable);

    /**
     * APPL-05: 대기 → 수락. 대기 상태일 때만 바뀌고, 바뀐 행 수를 돌려줍니다.
     * 0이면 이미 다른 요청이 처리한 지원입니다 (동시에 두 번 눌러도 한 번만 성공).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Application a
            set a.status = :accepted, a.decidedAt = :now
            where a.id = :id and a.status = :pending
            """)
    int markAccepted(@Param("id") Long id, @Param("now") LocalDateTime now,
            @Param("pending") ApplicationStatus pending, @Param("accepted") ApplicationStatus accepted);

    /** APPL-08: 역할이 마감되면 그 역할의 남은 대기 지원을 마감 처리 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Application a
            set a.status = :closed
            where a.postRole.id = :postRoleId and a.status = :pending
            """)
    int closePendingByPostRoleId(@Param("postRoleId") Long postRoleId,
            @Param("pending") ApplicationStatus pending, @Param("closed") ApplicationStatus closed);
}
