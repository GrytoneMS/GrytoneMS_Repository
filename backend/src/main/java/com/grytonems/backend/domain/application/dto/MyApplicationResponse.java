package com.grytonems.backend.domain.application.dto;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.application.Application;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.post.Post;

/** GET /api/users/me/applications (APPL-10) */
public record MyApplicationResponse(
        Long id,
        ApplicationStatus status,
        String roleName,
        PostSummary post,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {

    /** deleted가 true면 화면에서 제목 대신 "삭제된 모집글"로 표시합니다 */
    public record PostSummary(Long id, String title, boolean deleted) {
        static PostSummary from(Post post) {
            boolean deleted = post.getDeletedAt() != null;
            return new PostSummary(post.getId(), deleted ? null : post.getTitle(), deleted);
        }
    }

    public static MyApplicationResponse from(Application application) {
        return new MyApplicationResponse(application.getId(), application.getStatus(),
                application.getPostRole().getRoleName(), PostSummary.from(application.getPost()),
                application.getCreatedAt(), application.getDecidedAt());
    }
}
