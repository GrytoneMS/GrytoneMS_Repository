package com.grytonems.backend.domain.notification;

import java.time.LocalDateTime;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.grytonems.backend.domain.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * notification_settings (DB 설계서 7장): 사용자당 1행, 처음에는 전부 켜짐 (NOTI-03).
 * 앱 푸시에만 적용되고 알림함에는 항상 쌓입니다. 앱 푸시가 1차 범위 밖이라 1차에서는 저장·조회만 합니다.
 */
@Getter
@Entity
@Table(name = "notification_settings")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "apply_enabled", nullable = false)
    private boolean applyEnabled;

    @Column(name = "deadline_enabled", nullable = false)
    private boolean deadlineEnabled;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public NotificationSetting(User user) {
        this.user = user;
        this.applyEnabled = true;
        this.deadlineEnabled = true;
        this.quietHoursEnabled = true;
    }

    public void update(boolean applyEnabled, boolean deadlineEnabled, boolean quietHoursEnabled) {
        this.applyEnabled = applyEnabled;
        this.deadlineEnabled = deadlineEnabled;
        this.quietHoursEnabled = quietHoursEnabled;
    }
}
