package com.grytonems.backend.domain.verification;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * school_verifications (DB 설계서 4장): 학교 인증 이력.
 * 이메일 인증은 성공했을 때 APPROVED로 한 줄 남깁니다. 서류 인증(AUTH-07)은 PENDING으로 쌓였다가 관리자가 처리합니다.
 * 설계서대로 created_at만 있고 updated_at은 없습니다.
 */
@Getter
@Entity
@Table(name = "school_verifications")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus status;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(nullable = false, length = 10)
    private String semester;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 학교 이메일 인증 성공 기록 */
    public static SchoolVerification emailApproved(User user, School school, String semester) {
        SchoolVerification verification = new SchoolVerification();
        verification.user = user;
        verification.school = school;
        verification.method = VerificationMethod.EMAIL;
        verification.status = VerificationStatus.APPROVED;
        verification.semester = semester;
        return verification;
    }
}
