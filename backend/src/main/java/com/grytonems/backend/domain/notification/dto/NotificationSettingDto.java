package com.grytonems.backend.domain.notification.dto;

import com.grytonems.backend.domain.notification.NotificationSetting;

import jakarta.validation.constraints.NotNull;

/** GET·PUT /api/users/me/notification-settings (NOTI-03): 요청·응답 같은 모양, PUT은 세 값을 모두 보냅니다 */
public record NotificationSettingDto(

        @NotNull(message = "지원 알림 설정을 보내 주세요")
        Boolean applyEnabled,

        @NotNull(message = "마감 알림 설정을 보내 주세요")
        Boolean deadlineEnabled,

        @NotNull(message = "방해 금지 시간 설정을 보내 주세요")
        Boolean quietHoursEnabled
) {

    public static final NotificationSettingDto DEFAULT = new NotificationSettingDto(true, true, true);

    public static NotificationSettingDto from(NotificationSetting setting) {
        return new NotificationSettingDto(setting.isApplyEnabled(), setting.isDeadlineEnabled(),
                setting.isQuietHoursEnabled());
    }
}
