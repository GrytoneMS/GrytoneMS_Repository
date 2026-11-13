package com.grytonems.backend.domain.application;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.notification.NotificationService;
import com.grytonems.backend.domain.notification.NotificationTargetType;
import com.grytonems.backend.domain.notification.NotificationType;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;

import lombok.RequiredArgsConstructor;

/**
 * APPL-12 대기 지원 리마인드: 3일 넘게 대기 중인 지원이 있는 글의 팀장에게 글마다 한 건.
 * 같은 지원으로는 한 번만 보냅니다(reminded_at). 마감일까지 처리하지 않으면 자동 마감(POST-06)으로 CLOSED가 됩니다.
 */
@Service
@RequiredArgsConstructor
public class ApplicationReminderService {

    public static final int REMIND_AFTER_DAYS = 3;

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /** @return 보낸 알림 수 */
    @Transactional
    public int remind(LocalDateTime now) {
        List<Application> targets = applicationRepository.findToRemind(ApplicationStatus.PENDING,
                now.minusDays(REMIND_AFTER_DAYS));
        if (targets.isEmpty()) {
            return 0;
        }
        Map<Post, List<Application>> byPost = targets.stream()
                .collect(Collectors.groupingBy(Application::getPost, LinkedHashMap::new, Collectors.toList()));

        // 팀장이 정지 중인 글은 아무에게도 보이지 않으므로 알리지 않음 (ADMN-04). 정지가 풀린 뒤 보냅니다
        Set<Long> leaderIds = byPost.keySet().stream().map(Post::getLeaderId).collect(Collectors.toSet());
        Set<Long> suspended = userRepository.findAllById(leaderIds).stream()
                .filter(u -> u.getStatus() == UserStatus.SUSPENDED || u.getStatus() == UserStatus.WITHDRAWN)
                .map(User::getId)
                .collect(Collectors.toSet());

        List<Long> remindedIds = new java.util.ArrayList<>();
        int sent = 0;
        for (Map.Entry<Post, List<Application>> entry : byPost.entrySet()) {
            Post post = entry.getKey();
            if (suspended.contains(post.getLeaderId())) {
                continue;
            }
            List<Application> apps = entry.getValue();
            notificationService.notify(post.getLeaderId(), NotificationType.APPLICATION_REMINDER,
                    NotificationTargetType.POST, post.getId(),
                    "아직 확인하지 않은 지원자가 " + apps.size() + "명 있어요");
            apps.forEach(a -> remindedIds.add(a.getId()));
            sent++;
        }
        if (!remindedIds.isEmpty()) {
            applicationRepository.markReminded(remindedIds, now);
        }
        return sent;
    }
}
