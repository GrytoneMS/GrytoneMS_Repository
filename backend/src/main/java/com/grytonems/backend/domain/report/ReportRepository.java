package com.grytonems.backend.domain.report;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.grytonems.backend.domain.user.User;

public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    /** SAFE-02: 아직 처리 안 된 신고 수 (UQ 때문에 신고자는 모두 다른 사람) */
    long countByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);

    /** ADMN-02 처리 결과 알림 대상: 같은 대상의 미처리 신고자 */
    @Query("""
            select r.reporter.id from Report r
            where r.targetType = :targetType and r.targetId = :targetId and r.status = :status
            """)
    List<Long> findReporterIds(@Param("targetType") ReportTargetType targetType, @Param("targetId") Long targetId,
            @Param("status") ReportStatus status);

    /** ADMN-02: 같은 대상의 미처리 신고를 한 번에 처리완료로 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Report r
            set r.status = :resolved, r.action = :action, r.handledBy = :admin, r.handledAt = :now
            where r.targetType = :targetType and r.targetId = :targetId and r.status = :received
            """)
    int resolveAll(@Param("targetType") ReportTargetType targetType, @Param("targetId") Long targetId,
            @Param("action") ReportAction action, @Param("admin") User admin, @Param("now") LocalDateTime now,
            @Param("received") ReportStatus received, @Param("resolved") ReportStatus resolved);

    /** ADMN-02 신고 목록: 최신순, status 없으면 전체 */
    @Query("""
            select r from Report r
            join fetch r.reporter
            where (:status is null or r.status = :status)
              and (:cursor is null or r.id < :cursor)
            order by r.id desc
            """)
    List<Report> findForAdmin(@Param("status") ReportStatus status, @Param("cursor") Long cursor, Pageable pageable);
}
