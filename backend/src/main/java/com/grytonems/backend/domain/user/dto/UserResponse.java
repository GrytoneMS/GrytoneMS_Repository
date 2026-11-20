package com.grytonems.backend.domain.user.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRole;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.domain.verification.ReverifyPolicy;

/**
 * 로그인 응답의 user, GET /api/users/me 응답.
 * 화면은 status로 버튼을 나눕니다 (VERIFIED면 글쓰기·지원 가능).
 * nicknameChangeableAt: 닉네임을 다시 바꿀 수 있는 날, null이면 지금 가능 (AUTH-03)
 * reverifyDueDate: 학기 재인증 기한, 재인증 기간이 아니면 null (AUTH-08)
 */
public record UserResponse(
        Long id,
        String nickname,
        UserStatus status,
        UserRole role,
        SchoolSummary school,
        String profileImageUrl,
        Suspension suspension,
        LocalDate nicknameChangeableAt,
        LocalDate reverifyDueDate
) {

    /** 정지 중일 때만 채워집니다. endsAt이 null이면 영구 정지 (ADMN-04) */
    public record Suspension(String reason, LocalDateTime endsAt) {
    }

    public record SchoolSummary(Long id, String name, String campus) {

        static SchoolSummary from(School school) {
            return school == null ? null : new SchoolSummary(school.getId(), school.getName(), school.getCampus());
        }
    }

    public static UserResponse from(User user) {
        return from(user, null);
    }

    public static UserResponse from(User user, Suspension suspension) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                user.getStatus(),
                user.getRole(),
                SchoolSummary.from(user.getSchool()),
                user.getProfileImageUrl(),
                suspension,
                user.nicknameChangeableAt(LocalDateTime.now()),
                ReverifyPolicy.dueDateOf(user, LocalDate.now())
        );
    }
}
