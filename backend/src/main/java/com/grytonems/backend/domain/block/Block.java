package com.grytonems.backend.domain.block;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** blocks (DB 설계서): 해제하면 행을 지웁니다. UQ (blocker_id, blocked_id) */
@Getter
@Entity
@Table(name = "blocks",
        indexes = @Index(name = "idx_blocks_blocked", columnList = "blocked_id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_blocks_blocker_blocked", columnNames = {"blocker_id", "blocked_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Block(User blocker, User blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
        this.createdAt = LocalDateTime.now();
    }
}
