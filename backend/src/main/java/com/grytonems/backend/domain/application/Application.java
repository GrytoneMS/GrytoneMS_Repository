package com.grytonems.backend.domain.application;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.global.common.BaseTimeEntity;

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
 * applications (DB 설계서 6장): 역할별 지원과 상태.
 * UQ(post_id, applicant_id): 글 하나에 한 번만 지원할 수 있고, 취소·거절 후에도 다시 지원할 수 없습니다.
 */
@Getter
@Entity
@Table(name = "applications",
        indexes = {
                @Index(name = "idx_applications_role_status", columnList = "post_role_id, status"),
                @Index(name = "idx_applications_applicant_status", columnList = "applicant_id, status"),
                @Index(name = "idx_applications_status_created", columnList = "status, created_at")
        },
        uniqueConstraints = @UniqueConstraint(name = "uq_applications_post_applicant",
                columnNames = {"post_id", "applicant_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_role_id", nullable = false)
    private PostRole postRole;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(name = "available_time", nullable = false, length = 100)
    private String availableTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason", length = 30)
    private RejectReason rejectReason;

    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Builder
    private Application(Post post, PostRole postRole, User applicant, String message, String availableTime) {
        this.post = post;
        this.postRole = postRole;
        this.applicant = applicant;
        this.message = message;
        this.availableTime = availableTime;
        this.status = ApplicationStatus.PENDING;
    }

    /** APPL-06 거절 (대기 상태에서만) */
    public void reject(RejectReason reason, LocalDateTime now) {
        this.status = ApplicationStatus.REJECTED;
        this.rejectReason = reason;
        this.decidedAt = now;
    }
}
