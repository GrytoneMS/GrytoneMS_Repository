package com.grytonems.backend.domain.user;

import java.time.LocalDate;
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
    /** 닉네임을 다시 바꿀 수 있기까지 걸리는 날 (AUTH-03) */
    public static final int NICKNAME_CHANGE_DAYS = 30;
    /** 탈퇴한 사용자의 닉네임 표시 (AUTH-09) */
    public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

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

    /** 기본 정보 수정 (PROF-01). 비운 항목은 null로 저장합니다 */
    public void updateProfile(String major, Integer grade, String bio) {
        this.major = major;
        this.grade = grade;
        this.bio = bio;
    }

    /** 이용 정지 (ADMN-04) */
    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    /** 정지 해제 (ADMN-04): 학교 인증을 마친 적이 있으면 인증 회원, 아니면 미인증으로 돌아갑니다 */
    public void liftSuspension() {
        this.status = (verifiedAt != null && school != null) ? UserStatus.VERIFIED : UserStatus.UNVERIFIED;
    }

    // ===== 계정 (AUTH-03·05·09) =====

    /** 화면에 보일 닉네임. 탈퇴한 사용자는 "탈퇴한 사용자"로 보입니다 (DB에는 겹치지 않는 값이 남음) */
    public String getNickname() {
        return status == UserStatus.WITHDRAWN ? WITHDRAWN_NICKNAME : nickname;
    }

    /** 닉네임을 다시 바꿀 수 있는 날. 지금 바꿀 수 있으면 null (AUTH-03) */
    public LocalDate nicknameChangeableAt(LocalDateTime now) {
        if (nicknameChangedAt == null) {
            return null;
        }
        LocalDate date = nicknameChangedAt.toLocalDate().plusDays(NICKNAME_CHANGE_DAYS);
        return date.isAfter(now.toLocalDate()) ? date : null;
    }

    public void changeNickname(String nickname, LocalDateTime now) {
        this.nickname = nickname;
        this.nicknameChangedAt = now;
    }

    /** 비밀번호 재설정 (AUTH-05). 로그인 잠금도 풉니다 */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        recordLoginSuccess();
    }

    /** 회원 탈퇴 (AUTH-09): 개인정보를 비우고 탈퇴 상태로 둡니다. 닉네임은 겹치지 않는 값으로 바꿉니다 */
    public void withdraw(LocalDateTime now) {
        this.email = null;
        this.schoolEmail = null;
        this.nickname = "탈퇴#" + id;
        this.major = null;
        this.grade = null;
        this.bio = null;
        this.profileImageUrl = null;
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = now;
    }

    // ===== 프로필 2단계 (PROF-01·08) =====

    public void changeProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void changeShowActivity(boolean showActivity) {
        this.showActivity = showActivity;
    }
}
