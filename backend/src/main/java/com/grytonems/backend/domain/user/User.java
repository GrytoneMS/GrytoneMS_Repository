package com.grytonems.backend.domain.user;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * users (DB 설계서 4장)
 *
 * 설계서와 다른 점 (11/5 백엔드 팀 결정):
 * - email: NOT NULL 제거. 탈퇴 시 개인정보를 비우기 위해 NULL 허용, UNIQUE는 유지
 * - password_hash: 카카오 로그인 1차 제외로 NOT NULL
 * - kakao_id: 2차 개발 때 추가
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    /** 로그인 연속 실패 허용 횟수 (AUTH-04) */
    public static final int MAX_LOGIN_FAIL = 5;
    /** 잠금 시간 (AUTH-04) */
    public static final int LOCK_MINUTES = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 12)
    private String nickname;

    @Column(name = "nickname_changed_at")
    private LocalDateTime nicknameChangedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @Column(name = "school_email", unique = true, length = 255)
    private String schoolEmail;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(length = 50)
    private String major;

    private Integer grade;

    @Column(length = 50)
    private String bio;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "show_activity", nullable = false)
    private boolean showActivity;

    @Builder
    private User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = UserRole.USER;
        this.status = UserStatus.UNVERIFIED;
        this.loginFailCount = 0;
        this.showActivity = true;
    }

    // ===== 로그인 잠금 (AUTH-04) =====

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** 실패 횟수를 올리고, 5회가 되면 10분 잠급니다. 잠겼으면 true */
    public boolean recordLoginFailure(LocalDateTime now) {
        loginFailCount++;
        if (loginFailCount >= MAX_LOGIN_FAIL) {
            loginFailCount = 0;
            lockedUntil = now.plusMinutes(LOCK_MINUTES);
            return true;
        }
        return false;
    }

    public void recordLoginSuccess() {
        loginFailCount = 0;
        lockedUntil = null;
    }

    // ===== 상태 변경 =====

    /** 학교 인증 완료 (AUTH-06·07에서 사용) */
    public void verify(School school, String schoolEmail, LocalDateTime now) {
        this.school = school;
        this.schoolEmail = schoolEmail;
        this.verifiedAt = now;
        this.status = UserStatus.VERIFIED;
    }

    /** 이용 정지 (ADMN-04에서 사용) */
    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }
}
