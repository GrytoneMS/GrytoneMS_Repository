package com.grytonems.backend.domain.user.dto;

import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRole;
import com.grytonems.backend.domain.user.UserStatus;

/**
 * 로그인 응답의 user, GET /api/users/me 응답.
 * 화면은 status로 버튼을 나눕니다 (VERIFIED면 글쓰기·지원 가능).
 */
public record UserResponse(
        Long id,
        String nickname,
        UserStatus status,
        UserRole role,
        SchoolSummary school,
        String profileImageUrl
) {

    public record SchoolSummary(Long id, String name, String campus) {

        static SchoolSummary from(School school) {
            return school == null ? null : new SchoolSummary(school.getId(), school.getName(), school.getCampus());
        }
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getNickname(),
                user.getStatus(),
                user.getRole(),
                SchoolSummary.from(user.getSchool()),
                user.getProfileImageUrl()
        );
    }
}
