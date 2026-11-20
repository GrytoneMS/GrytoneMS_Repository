package com.grytonems.backend.domain.post.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grytonems.backend.domain.post.MeetingType;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostCategory;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.post.RecruitStatus;
import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.user.User;

/**
 * LIST-01 목록 카드 하나 (API 명세서 GET /api/posts 응답 items)
 */
public record PostCardResponse(
        Long id,
        String title,
        PostCategory category,
        RecruitStatus status,
        LocalDate deadline,
        @JsonProperty("dDay") long dDay,
        MeetingType meetingType,
        Leader leader,
        List<RoleSummary> roles,
        @JsonProperty("isScrapped") boolean isScrapped,
        LocalDateTime createdAt
) {

    /** 팀장 요약 */
    public record Leader(Long id, String nickname, String schoolName) {

        static Leader of(Long leaderId, User user) {
            if (user == null) {
                return new Leader(leaderId, null, null);
            }
            String schoolName = user.getSchool() == null ? null : user.getSchool().getName();
            return new Leader(user.getId(), user.getNickname(), schoolName);
        }
    }

    /** 역할 요약 (remaining = 남은 인원) */
    public record RoleSummary(RoleCode roleCode, String roleName, int remaining) {

        static RoleSummary from(PostRole role) {
            return new RoleSummary(role.getRoleCode(), role.getRoleName(), role.getRemaining());
        }
    }

    /** isScrapped: 로그인한 사람이 스크랩한 글인지 (비회원은 false, SCRP-01 북마크 표시) */
    public static PostCardResponse of(Post post, List<PostRole> roles, User leader, LocalDate today,
                                      boolean isScrapped) {
        return new PostCardResponse(
                post.getId(),
                post.getTitle(),
                post.getCategory(),
                post.getStatus(),
                post.getDeadline(),
                ChronoUnit.DAYS.between(today, post.getDeadline()),
                post.getMeetingType(),
                Leader.of(post.getLeaderId(), leader),
                roles.stream().map(RoleSummary::from).toList(),
                isScrapped,
                post.getCreatedAt()
        );
    }
}