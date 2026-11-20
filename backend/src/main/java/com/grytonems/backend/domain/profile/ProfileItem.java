package com.grytonems.backend.domain.profile;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.grytonems.backend.domain.user.User;
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

/** profile_items (DB 설계서 5장): 경력·수상·자격증·포트폴리오. 삭제는 deleted_at 표시 */
@Getter
@Entity
@Table(name = "profile_items", indexes = @Index(name = "idx_profile_items_user", columnList = "user_id, item_type"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ProfileItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "sub_type", length = 20)
    private ExperienceType subType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 50)
    private String role;

    @Column(length = 50)
    private String issuer;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 300)
    private String description;

    @Column(length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemVisibility visibility;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private ProfileItem(User user, ProfileItemType itemType) {
        this.user = user;
        this.itemType = itemType;
        this.visibility = ItemVisibility.PUBLIC;
    }

    /** itemType은 바꾸지 않습니다. 그 종류에 쓰지 않는 칸은 서비스에서 null로 넘깁니다 */
    public void update(ExperienceType subType, String title, String role, String issuer, LocalDate startDate,
            LocalDate endDate, String description, String url, ItemVisibility visibility) {
        this.subType = subType;
        this.title = title;
        this.role = role;
        this.issuer = issuer;
        this.startDate = startDate;
        this.endDate = endDate;
        this.description = description;
        this.url = url;
        this.visibility = visibility == null ? ItemVisibility.PUBLIC : visibility;
    }

    public void delete(LocalDateTime now) {
        this.deletedAt = now;
    }
}
