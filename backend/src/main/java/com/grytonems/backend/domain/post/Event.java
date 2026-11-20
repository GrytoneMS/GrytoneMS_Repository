package com.grytonems.backend.domain.post;

import java.time.LocalDate;

import com.grytonems.backend.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공모전·대회 행사 정보 (DB 설계서 6장 events)
 * 공모전(CONTEST)·대회(COMPETITION) 글에만 1개 붙습니다.
 */
@Getter
@Entity
@Table(name = "events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 행사가 붙은 모집글 (글 하나에 행사 하나) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private Post post;

    /** 행사명 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 주최 */
    @Column(nullable = false, length = 100)
    private String host;

    /** 공식 링크 */
    @Column(name = "official_url", nullable = false, length = 500)
    private String officialUrl;

    /** 접수 마감일 */
    @Column(name = "apply_deadline", nullable = false)
    private LocalDate applyDeadline;

    @Builder
    private Event(Post post, String name, String host, String officialUrl, LocalDate applyDeadline) {
        this.post = post;
        this.name = name;
        this.host = host;
        this.officialUrl = officialUrl;
        this.applyDeadline = applyDeadline;
    }

    /** POST-02: 행사 정보 수정 */
    public void update(String name, String host, String officialUrl, LocalDate applyDeadline) {
        this.name = name;
        this.host = host;
        this.officialUrl = officialUrl;
        this.applyDeadline = applyDeadline;
    }
}