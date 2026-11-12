package com.grytonems.backend.domain.post.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grytonems.backend.domain.application.Application;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.post.Event;
import com.grytonems.backend.domain.post.LocationCode;
import com.grytonems.backend.domain.post.MeetingType;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostCategory;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.post.RecruitStatus;
import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.user.User;

/**
 * LIST-05 모집글 상세 (API 명세서 GET /api/posts/{postId})
 * 연락 수단(contactLink)은 넣지 않습니다. 수락된 지원자만 APPL-07로 받습니다.
 */
public record PostDetailResponse(
        Long id,
        String title,
        PostCategory category,
        String description,
        RecruitStatus status,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate deadline,
        @JsonProperty("dDay") long dDay,
        MeetingType meetingType,
        LocationCode locationCode,
        String locationText,
        String meetingFreq,
        EventInfo event,
        List<RoleDetail> roles,
        Leader leader,
        @JsonProperty("isMine") boolean isMine,
        MyApplication myApplication,
        @JsonProperty("isScrapped") boolean isScrapped,
        LocalDateTime createdAt
) {

    /** 행사 정보 (공모전·대회만, 아니면 null) */
    public record EventInfo(String name, String host, String officialUrl, LocalDate applyDeadline) {

        static EventInfo from(Event event) {
            if (event == null) {
                return null;
            }
            return new EventInfo(event.getName(), event.getHost(), event.getOfficialUrl(), event.getApplyDeadline());
        }
    }

    /** 역할 하나 (지원할 때 id를 postRoleId로 보냅니다) */
    public record RoleDetail(Long id, RoleCode roleCode, String roleName, int headcount, int filledCount,
                             String requirements, RecruitStatus status) {

        static RoleDetail from(PostRole role) {
            return new RoleDetail(role.getId(), role.getRoleCode(), role.getRoleName(), role.getHeadcount(),
                    role.getFilledCount(), role.getRequirements(), role.getStatus());
        }
    }

    /** 팀장 프로필 요약 */
    public record Leader(Long id, String nickname, String schoolName, String major, String profileImageUrl) {

        static Leader of(Long leaderId, User user) {
            if (user == null) {
                return new Leader(leaderId, null, null, null, null);
            }
            String schoolName = user.getSchool() == null ? null : user.getSchool().getName();
            return new Leader(user.getId(), user.getNickname(), schoolName, user.getMajor(), user.getProfileImageUrl());
        }
    }

    /** 내가 이 글에 지원했으면 채워짐 (아니면 null) */
    public record MyApplication(Long id, Long postRoleId, ApplicationStatus status) {

        static MyApplication from(Application application) {
            if (application == null) {
                return null;
            }
            return new MyApplication(application.getId(), application.getPostRole().getId(), application.getStatus());
        }
    }

    public static PostDetailResponse of(Post post, List<PostRole> roles, Event event, User leader,
                                        boolean isMine, Application myApplication, boolean isScrapped,
                                        LocalDate today) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getCategory(),
                post.getDescription(),
                post.getStatus(),
                post.getPeriodStart(),
                post.getPeriodEnd(),
                post.getDeadline(),
                ChronoUnit.DAYS.between(today, post.getDeadline()),
                post.getMeetingType(),
                post.getLocationCode(),
                post.getLocationText(),
                post.getMeetingFreq(),
                EventInfo.from(event),
                roles.stream().map(RoleDetail::from).toList(),
                Leader.of(post.getLeaderId(), leader),
                isMine,
                MyApplication.from(myApplication),
                isScrapped,
                post.getCreatedAt()
        );
    }
}