package com.grytonems.backend.domain.verification;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolVerificationRepository extends JpaRepository<SchoolVerification, Long> {

    /** GET /api/verifications/me: 가장 최근 신청 */
    Optional<SchoolVerification> findFirstByUserIdOrderByIdDesc(Long userId);

    boolean existsByUserIdAndMethodAndStatus(Long userId, VerificationMethod method, VerificationStatus status);

    /** ADMN-03 대기 목록: 오래된 순 (24시간 처리 목표) */
    @Query("""
            select v from SchoolVerification v
            join fetch v.user join fetch v.school
            where v.method = :method and v.status = :status and (:cursor is null or v.id > :cursor)
            order by v.id asc
            """)
    List<SchoolVerification> findOldestFirst(@Param("method") VerificationMethod method,
            @Param("status") VerificationStatus status, @Param("cursor") Long cursor, Pageable pageable);

    /** ADMN-03 처리된 목록: 최신순 */
    @Query("""
            select v from SchoolVerification v
            join fetch v.user join fetch v.school
            where v.method = :method and v.status = :status and (:cursor is null or v.id < :cursor)
            order by v.id desc
            """)
    List<SchoolVerification> findNewestFirst(@Param("method") VerificationMethod method,
            @Param("status") VerificationStatus status, @Param("cursor") Long cursor, Pageable pageable);
}
