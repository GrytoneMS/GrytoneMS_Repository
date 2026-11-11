package com.grytonems.backend.domain.post.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostCategory;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.post.RecruitStatus;
import com.grytonems.backend.domain.post.RoleCode;

/**
 * GET /api/users/me/posts (APPL-11 내 모집글) 항목 하나.
 * applicantCount = 지원자 목록에 보이는 수 (취소 제외), pendingCount = 아직 처리하지 않은 대기 지원 수
 */
public record MyPostResponse(
        Long id,
        String title,
        PostCategory category,
        RecruitStatus status,
        LocalDate deadline,
        @JsonProperty("dDay") long dDay,
        int applicantCount,
        int pendingCount,
        List<Role> roles,
        LocalDateTime createdAt,
        LocalDateTime closedAt
) {

    /** 역할별 인원과 지원자 수 */
    public record Role(Long id, RoleCode roleCode, String roleName, int headcount, int filledCount,
                       RecruitStatus status, int applicantCount, int pendingCount) {
    }

    /** 역할 id별 [지원자 수, 대기 수] */
    public record Counts(int applicants, int pending) {
        public static final Counts ZERO = new Counts(0, 0);
    }

    public static MyPostResponse of(Post post, List<PostRole> roles, Map<Long, Counts> countsByRole,
                                    LocalDate today) {
        List<Role> roleItems = roles.stream()
                .map(role -> {
                    Counts counts = countsByRole.getOrDefault(role.getId(), Counts.ZERO);
                    return new Role(role.getId(), role.getRoleCode(), role.getRoleName(), role.getHeadcount(),
                            role.getFilledCount(), role.getStatus(), counts.applicants(), counts.pending());
                })
                .toList();
        return new MyPostResponse(
                post.getId(),
                post.getTitle(),
                post.getCategory(),
                post.getStatus(),
                post.getDeadline(),
                ChronoUnit.DAYS.between(today, post.getDeadline()),
                roleItems.stream().mapToInt(Role::applicantCount).sum(),
                roleItems.stream().mapToInt(Role::pendingCount).sum(),
                roleItems,
                post.getCreatedAt(),
                post.getClosedAt()
        );
    }
}
