package com.grytonems.backend.domain.profile;

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

/** user_skills (DB 설계서 5장): 보유 기술·툴, 1인 최대 10개 (PROF-03) */
@Getter
@Entity
@Table(name = "user_skills",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_skills", columnNames = {"user_id", "name"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String name;

    public UserSkill(User user, String name) {
        this.user = user;
        this.name = name;
    }
}
