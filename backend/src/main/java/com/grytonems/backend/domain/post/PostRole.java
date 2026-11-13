package com.grytonems.backend.domain.post;

import com.grytonems.backend.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모집글 안의 역할 (DB 설계서 6장 post_roles)
 * 글 하나에 최대 5개. 지원은 역할 단위로 받습니다.
 */
@Getter
@Entity
@Table(name = "post_roles",
        indexes = @Index(name = "idx_post_roles_role_status", columnList = "role_code, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostRole extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 역할이 속한 모집글 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 20)
    private RoleCode roleCode;

    /** 화면 표시 이름 (예: 영상 편집) */
    @Column(name = "role_name", nullable = false, length = 30)
    private String roleName;

    /** 모집 인원 1~5 */
    @Column(nullable = false)
    private int headcount;

    /** 수락된 인원 */
    @Column(name = "filled_count", nullable = false)
    private int filledCount;

    @Column(length = 100)
    private String requirements;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecruitStatus status;

    @Builder
    private PostRole(Post post, RoleCode roleCode, String roleName, int headcount, String requirements) {
        this.post = post;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.headcount = headcount;
        this.requirements = requirements;
        this.filledCount = 0;               // 처음엔 아무도 수락 안 됨
        this.status = RecruitStatus.OPEN;   // 처음엔 모집중
    }

    /** 남은 인원 (API 명세서의 remaining) */
    public int getRemaining() {
        return headcount - filledCount;
    }

    /** POST-04: 글을 수동 마감하면 역할도 마감 */
    public void close() {
        this.status = RecruitStatus.CLOSED;
    }
}
