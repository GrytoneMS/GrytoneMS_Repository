package com.grytonems.backend.domain.profile.dto;

import java.util.List;

import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserStatus;

/**
 * 프로필 (내 프로필·타인 프로필 공통).
 * verified: 학교 인증 배지 표시 여부
 * roleTags: 희망 역할 코드 (최대 3개), skills: 보유 기술·툴 (최대 10개)
 */
public record ProfileResponse(
        Long id,
        String nickname,
        boolean verified,
        SchoolSummary school,
        String major,
        Integer grade,
        String bio,
        String profileImageUrl,
        List<RoleCode> roleTags,
        List<String> skills
) {

    public record SchoolSummary(Long id, String name, String campus) {
        static SchoolSummary from(School school) {
            return school == null ? null : new SchoolSummary(school.getId(), school.getName(), school.getCampus());
        }
    }

    public static ProfileResponse of(User user, List<RoleCode> roleTags, List<String> skills) {
        return new ProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getStatus() == UserStatus.VERIFIED,
                SchoolSummary.from(user.getSchool()),
                user.getMajor(),
                user.getGrade(),
                user.getBio(),
                user.getProfileImageUrl(),
                roleTags,
                skills);
    }
}
