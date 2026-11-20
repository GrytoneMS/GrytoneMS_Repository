package com.grytonems.backend.domain.report;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * reports (DB 설계서): 같은 사람이 같은 대상을 두 번 신고할 수 없습니다 (UQ reporter_id, target_type, target_id)
 * 대상은 종류가 여러 개라 target_type + target_id로 가리킵니다 (FK 없음).
 */
@Getter
@Entity
@Table(name = "reports",
        indexes = @Index(name = "idx_reports_target", columnList = "target_type, target_id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_reports_reporter_target",
                columnNames = {"reporter_id", "target_type", "target_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 20)
    private ReportReason reasonCode;

    @Column(length = 200)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReportAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Report(User reporter, ReportTargetType targetType, Long targetId, ReportReason reasonCode,
                   String detail) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reasonCode = reasonCode;
        this.detail = detail;
        this.status = ReportStatus.RECEIVED;
        this.createdAt = LocalDateTime.now();
    }
}
