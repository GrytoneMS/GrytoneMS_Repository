package com.grytonems.backend.domain.application.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.grytonems.backend.domain.application.Application;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserStatus;

/** GET /api/posts/{postId}/applications (APPL-04): 역할 하나와 그 역할의 지원자들 */
public record RoleApplicantsResponse(
        Long postRoleId,
        String roleName,
        int headcount,
        int filledCount,
        List<Item> applications
) {

    public record Item(
            Long id,
            ApplicationStatus status,
            String message,
            String availableTime,
            LocalDateTime createdAt,
            Applicant applicant
    ) {
        public static Item from(Application application) {
            return new Item(application.getId(), application.getStatus(), application.getMessage(),
                    application.getAvailableTime(), application.getCreatedAt(),
                    Applicant.from(application.getApplicant()));
        }
    }

    public record Applicant(
            Long id,
            String nickname,
            String schoolName,
            String major,
            Integer grade,
            String profileImageUrl
    ) {
        static Applicant from(User user) {
            if (user.getStatus() == UserStatus.WITHDRAWN) {
                return new Applicant(user.getId(), "탈퇴한 사용자", null, null, null, null);
            }
            String schoolName = user.getSchool() == null ? null : user.getSchool().getName();
            return new Applicant(user.getId(), user.getNickname(), schoolName, user.getMajor(), user.getGrade(),
                    user.getProfileImageUrl());
        }
    }

    public static RoleApplicantsResponse of(PostRole role, List<Application> applications) {
        return new RoleApplicantsResponse(role.getId(), role.getRoleName(), role.getHeadcount(),
                role.getFilledCount(), applications.stream().map(Item::from).toList());
    }
}
