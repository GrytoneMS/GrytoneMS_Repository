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
 * items: 경력·수상·자격증·포트폴리오 (다른 사람에게는 PUBLIC만, 지원자 프로필에는 모두)
 * activities: 수락된 팀 이력 (다른 사람에게는 showActivity가 true일 때만)
 * showActivity, completeness: 내 프로필에만 들어 있고 다른 사람 프로필에서는 null
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
        List<String> skills,
        List<ProfileItemResponse> items,
        List<ActivityResponse> activities,
        Boolean showActivity,
        Integer completeness
) {

    public record SchoolSummary(Long id, String name, String campus) {
        static SchoolSummary from(School school) {
            return school == null ? null : new SchoolSummary(school.getId(), school.getName(), school.getCampus());
        }
    }

    public static ProfileResponse of(User user, List<RoleCode> roleTags, List<String> skills,
            List<ProfileItemResponse> items, List<ActivityResponse> activities, Boolean showActivity,
            Integer completeness) {
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
                skills,
                items,
                activities,
                showActivity,
                completeness);
    }
}
