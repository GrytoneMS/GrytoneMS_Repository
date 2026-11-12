package com.grytonems.backend.domain.user;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.report.Report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * user_suspensions (DB 설계서 그대로): 이용 정지 이력 (ADMN-04)
 * 지금 걸려 있는 정지 = 정지 중(SUSPENDED)인 사용자의 가장 최근 행.
 * 관리자가 기간 전에 풀면 ends_at을 푼 시각으로 바꿔 기록합니다.
 */
@Getter
@Entity
@Table(name = "user_suspensions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSuspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 근거 신고 (없을 수 있음) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private Report report;

    /** 사용자에게 보여줄 사유 */
    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    /** null이면 영구 정지 */
    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private UserSuspension(User user, Report report, String reason, LocalDateTime startsAt, LocalDateTime endsAt,
                           User createdBy) {
        this.user = user;
        this.report = report;
        this.reason = reason;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdBy = createdBy;
        this.createdAt = startsAt;
    }

    /** 관리자가 기간 전에 해제: 끝나는 시각을 지금으로 */
    public void endNow(LocalDateTime now) {
        this.endsAt = now;
    }
}
