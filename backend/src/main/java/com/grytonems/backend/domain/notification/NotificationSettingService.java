package com.grytonems.backend.domain.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.notification.dto.NotificationSettingDto;
import com.grytonems.backend.domain.user.UserRepository;

import lombok.RequiredArgsConstructor;

/** NOTI-03 알림 설정. 행이 없으면 전부 켜진 것으로 봅니다 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

    private final NotificationSettingRepository settingRepository;
    private final UserRepository userRepository;

    public NotificationSettingDto get(Long userId) {
        return settingRepository.findById(userId)
                .map(NotificationSettingDto::from)
                .orElse(NotificationSettingDto.DEFAULT);
    }

    @Transactional
    public NotificationSettingDto update(Long userId, NotificationSettingDto request) {
        NotificationSetting setting = settingRepository.findById(userId)
                .orElseGet(() -> settingRepository.save(new NotificationSetting(userRepository.getReferenceById(userId))));
        setting.update(request.applyEnabled(), request.deadlineEnabled(), request.quietHoursEnabled());
        return NotificationSettingDto.from(setting);
    }
}
