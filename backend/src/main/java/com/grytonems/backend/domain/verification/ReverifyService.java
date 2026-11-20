package com.grytonems.backend.domain.verification;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.notification.NotificationService;
import com.grytonems.backend.domain.notification.NotificationType;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;

import lombok.RequiredArgsConstructor;

/** 학기 재인증 배치 (AUTH-08). 실행 시각은 ReverifyScheduler, 여기서는 "오늘"을 받아 처리만 합니다 */
@Service
@RequiredArgsConstructor
public class ReverifyService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /** 학기 시작일(3/1·9/1)이면 그 전에 인증한 회원에게 REVERIFY_REQUIRED. 보낸 수 */
    @Transactional
    public int notifyReverify(LocalDate today) {
        LocalDate start = ReverifyPolicy.semesterStart(today);
        if (!today.equals(start)) {
            return 0;
        }
        List<Long> userIds = userRepository.findIdsToReverify(UserStatus.VERIFIED, start.atStartOfDay());
        LocalDate due = ReverifyPolicy.dueDate(start);
        notificationService.notifyAll(userIds, NotificationType.REVERIFY_REQUIRED, null, null,
                "이번 학기 학교 인증을 다시 해주세요 (" + due.getMonthValue() + "월 " + due.getDayOfMonth() + "일까지)");
        return userIds.size();
    }

    /** 재인증 기한이 지났으면 아직 다시 인증하지 않은 회원을 미인증으로. 바뀐 수 */
    @Transactional
    public int expire(LocalDate today) {
        LocalDate start = ReverifyPolicy.semesterStart(today);
        if (!today.isAfter(ReverifyPolicy.dueDate(start))) {
            return 0;
        }
        return userRepository.expireVerification(start.atStartOfDay(), UserStatus.VERIFIED, UserStatus.UNVERIFIED);
    }
}
