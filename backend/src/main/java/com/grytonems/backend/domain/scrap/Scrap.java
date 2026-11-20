package com.grytonems.backend.domain.scrap;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * scraps (DB 설계서): 사용자 한 명이 글 하나를 한 번만 스크랩 (UQ user_id, post_id)
 * 설계서대로 created_at만 있고 updated_at은 없습니다.
 */
@Getter
@Entity
@Table(name = "scraps",
        uniqueConstraints = @UniqueConstraint(name = "uq_scraps_user_post", columnNames = {"user_id", "post_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Scrap(User user, Post post) {
        this.user = user;
        this.post = post;
        this.createdAt = LocalDateTime.now();
    }
}
