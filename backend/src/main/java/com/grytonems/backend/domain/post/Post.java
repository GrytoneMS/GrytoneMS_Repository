package com.grytonems.backend.domain.post;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.grytonems.backend.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모집글 (DB 설계서 6장 posts)
 */
@Getter
@Entity
@Table(name = "posts", indexes = {
        // DB 설계서 9장. 최신순은 id로 정렬하므로 created_at 대신 기본키(id)가 뒤에 붙는 3열 인덱스로 둡니다
        @Index(name = "idx_posts_status_hidden_deleted", columnList = "status, is_hidden, deleted_at"),
        @Index(name = "idx_posts_status_deadline", columnList = "status, deadline"),
        @Index(name = "idx_posts_leader_status", columnList = "leader_id, status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 팀장 id. User 엔티티가 생기면 User와 연결하도록 바꿉니다 */
    @Column(name = "leader_id", nullable = false)
    private Long leaderId;

    @Column(nullable = false, length = 40)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "meeting_type", nullable = false, length = 20)
    private MeetingType meetingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_code", length = 30)
    private LocationCode locationCode;

    @Column(name = "location_text", length = 100)
    private String locationText;

    @Column(name = "meeting_freq", length = 30)
    private String meetingFreq;

    @Column(name = "contact_link", nullable = false, length = 500)
    private String contactLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecruitStatus status;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private Post(Long leaderId, String title, PostCategory category, String description,
                 LocalDate periodStart, LocalDate periodEnd, LocalDate deadline,
                 MeetingType meetingType, LocationCode locationCode, String locationText,
                 String meetingFreq, String contactLink) {
        this.leaderId = leaderId;
        this.title = title;
        this.category = category;
        this.description = description;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.deadline = deadline;
        this.meetingType = meetingType;
        this.locationCode = locationCode;
        this.locationText = locationText;
        this.meetingFreq = meetingFreq;
        this.contactLink = contactLink;
        this.status = RecruitStatus.OPEN;   // 새 글은 항상 모집중
        this.hidden = false;                // 새 글은 숨김 아님
    }

    /** POST-04: 수동 마감 */
    public void close() {
        this.status = RecruitStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    /** SAFE-02·ADMN-02: 숨김 (신고 누적·관리자 조치). 모집 상태는 그대로 둡니다 */
    public void hide() {
        this.hidden = true;
    }

    /** ADMN-02: 관리자 복구 → 숨김 전 상태로 */
    public void restore() {
        this.hidden = false;
    }
}