package com.grytonems.backend.domain.profile;

import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** user_role_tags (DB 설계서 5장): 희망 역할 태그, 1인 최대 3개 (PROF-02) */
@Getter
@Entity
@Table(name = "user_role_tags",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_role_tags", columnNames = {"user_id", "role_code"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRoleTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 20)
    private RoleCode roleCode;

    public UserRoleTag(User user, RoleCode roleCode) {
        this.user = user;
        this.roleCode = roleCode;
    }
}
