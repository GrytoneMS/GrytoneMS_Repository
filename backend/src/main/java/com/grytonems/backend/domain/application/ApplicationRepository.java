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

    /** APPL-08: 역할 마감 알림을 보낼 대상 (그 역할에 특정 상태로 남아 있는 지원자 id) */
    @Query("select a.applicant.id from Application a where a.postRole.id = :postRoleId and a.status = :status")
    List<Long> findApplicantIdsByPostRoleIdAndStatus(@Param("postRoleId") Long postRoleId,
            @Param("status") ApplicationStatus status);

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

    /**
     * APPL-03: 대기 → 취소. 본인 지원이고 대기 상태일 때만 바뀌고, 바뀐 행 수를 돌려줍니다.
     * 팀장이 같은 순간 수락·거절해도 둘 중 하나만 성공합니다. 취소한 시각은 decidedAt에 남깁니다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Application a
            set a.status = :canceled, a.decidedAt = :now
            where a.id = :id and a.applicant.id = :applicantId and a.status = :pending
            """)
    int cancelPending(@Param("id") Long id, @Param("applicantId") Long applicantId, @Param("now") LocalDateTime now,
            @Param("pending") ApplicationStatus pending, @Param("canceled") ApplicationStatus canceled);

    /** APPL-11: 글 여러 개의 역할별·상태별 지원 수 */
    @Query("""
            select new com.grytonems.backend.domain.application.RoleApplicationCount(a.postRole.id, a.status, count(a))
            from Application a
            where a.post.id in :postIds
            group by a.postRole.id, a.status
            """)
    List<RoleApplicationCount> countByRoleAndStatus(@Param("postIds") List<Long> postIds);

    /** SAFE-03: 두 사람 사이의 대기 지원(양쪽 방향)을 한 번에 마감. 알림은 보내지 않습니다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Application a set a.status = :closed
            where a.status = :pending
              and ((a.applicant.id = :userA and a.post.id in (select p.id from Post p where p.leaderId = :userB))
                or (a.applicant.id = :userB and a.post.id in (select p.id from Post p where p.leaderId = :userA)))
            """)
    int closePendingBetween(@Param("userA") Long userA, @Param("userB") Long userB,
            @Param("pending") ApplicationStatus pending, @Param("closed") ApplicationStatus closed);

    /** ADMN-04: 이 사람이 팀장인 글의 대기 지원자와 글 제목 */
    @Query("""
            select new com.grytonems.backend.domain.application.PendingApplicant(a.applicant.id, a.post.title)
            from Application a
            where a.status = :pending and a.post.leaderId = :leaderId
            """)
    List<PendingApplicant> findPendingApplicantsOfLeader(@Param("leaderId") Long leaderId,
            @Param("pending") ApplicationStatus pending);

    /** ADMN-04: 이 사람이 팀장인 글의 대기 지원을 한 번에 마감 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Application a set a.status = :closed
            where a.status = :pending and a.post.id in (select p.id from Post p where p.leaderId = :leaderId)
            """)
    int closePendingOfLeader(@Param("leaderId") Long leaderId, @Param("pending") ApplicationStatus pending,
            @Param("closed") ApplicationStatus closed);

    /** APPL-09: 수락 → 나감. 수락 상태일 때만 바뀌고 바뀐 행 수를 돌려줍니다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Application a set a.status = :left, a.leftAt = :now
            where a.id = :id and a.status = :accepted
            """)
    int markLeft(@Param("id") Long id, @Param("now") LocalDateTime now,
            @Param("accepted") ApplicationStatus accepted, @Param("left") ApplicationStatus left);

    /** AUTH-09: 수락돼 참여 중인 팀 수 */
    long countByApplicantIdAndStatus(Long applicantId, ApplicationStatus status);

    /** AUTH-09: 탈퇴하는 사람의 대기 지원을 모두 취소 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Application a set a.status = :canceled, a.decidedAt = :now
            where a.applicant.id = :applicantId and a.status = :pending
            """)
    int cancelAllPending(@Param("applicantId") Long applicantId, @Param("now") LocalDateTime now,
            @Param("pending") ApplicationStatus pending, @Param("canceled") ApplicationStatus canceled);
}
