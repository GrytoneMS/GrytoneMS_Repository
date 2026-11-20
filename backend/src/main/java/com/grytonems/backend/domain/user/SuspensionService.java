package com.grytonems.backend.domain.user;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.application.PendingApplicant;
import com.grytonems.backend.domain.notification.NotificationService;
import com.grytonems.backend.domain.notification.NotificationType;
import com.grytonems.backend.domain.report.Report;
import com.grytonems.backend.domain.report.ReportRepository;
import com.grytonems.backend.domain.user.dto.SuspensionResponse;
import com.grytonems.backend.domain.user.dto.UserResponse;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * ADMN-04 이용 정지·해제.
 * - 정지: 7일 / 30일 / 영구. 글을 따로 숨겨 두지 않고, 목록·상세·지원·스크랩이 "팀장이 정지 중인 글"을 걸러서 보여줍니다.
 *   그래서 정지가 풀리면 글은 자동으로 다시 보입니다. 정지 시점에 그 사람 글의 대기 지원은 마감하고 지원자에게 알립니다.
 * - 해제: 관리자가 직접 풀거나 기간이 끝나면(SuspensionScheduler) 이전 상태(인증·미인증)로 돌아갑니다.
 * - 정지 중에도 로그인은 되고, 로그인·내 정보 응답의 suspension으로 사유·기간을 안내합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SuspensionService {

    private final UserRepository userRepository;
    private final UserSuspensionRepository suspensionRepository;
    private final ReportRepository reportRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;

    public SuspensionResponse suspend(Long adminId, Long userId, Integer days, String reason, Long reportId) {
        if (days != null && days != 7 && days != 30) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정지 기간은 7일, 30일, 영구 중에서 골라 주세요");
        }
        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() != UserStatus.WITHDRAWN)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요"));
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자는 정지할 수 없어요");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.ALREADY_SUSPENDED);
        }
        Report report = (reportId == null) ? null : reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없어요"));
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endsAt = (days == null) ? null : now.plusDays(days);
        suspensionRepository.save(UserSuspension.builder()
                .user(user)
                .report(report)
                .reason(reason.trim())
                .startsAt(now)
                .endsAt(endsAt)
                .createdBy(admin)
                .build());
        user.suspend();

        // 그 사람 글에 걸린 대기 지원은 마감 (글이 안 보이게 되므로 이동 대상 없이 알림)
        List<PendingApplicant> pending = applicationRepository.findPendingApplicantsOfLeader(userId,
                ApplicationStatus.PENDING);
        applicationRepository.closePendingOfLeader(userId, ApplicationStatus.PENDING, ApplicationStatus.CLOSED);
        for (PendingApplicant applicant : pending) {
            notificationService.notify(applicant.applicantId(), NotificationType.POST_CLOSED, null, null,
                    "'" + applicant.postTitle() + "' 모집이 마감되었어요");
        }
        return new SuspensionResponse(userId, UserStatus.SUSPENDED, endsAt);
    }

    /** 관리자가 기간 전에 해제: 정지의 끝나는 시각을 지금으로 바꾸고 이전 상태로 */
    public SuspensionResponse lift(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.SUSPENDED)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_SUSPENDED));
        suspensionRepository.findFirstByUserIdOrderByIdDesc(userId)
                .ifPresent(s -> s.endNow(LocalDateTime.now()));
        user.liftSuspension();
        return new SuspensionResponse(userId, user.getStatus(), null);
    }

    /** 기간이 끝난 정지를 풀어야 할 사용자 id (스케줄러가 한 명씩 liftIfExpired 호출) */
    @Transactional(readOnly = true)
    public List<Long> findExpiredUserIds(LocalDateTime now) {
        return suspensionRepository.findExpiredUserIds(UserStatus.SUSPENDED, now);
    }

    /** 기간이 끝났으면 해제. 그사이 관리자가 풀었으면 false */
    public boolean liftIfExpired(Long userId, LocalDateTime now) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.SUSPENDED)
                .orElse(null);
        if (user == null) {
            return false;
        }
        boolean expired = suspensionRepository.findFirstByUserIdOrderByIdDesc(userId)
                .map(s -> s.getEndsAt() != null && !s.getEndsAt().isAfter(now))
                .orElse(false);
        if (expired) {
            user.liftSuspension();
        }
        return expired;
    }

    /** 로그인·내 정보 응답에 넣을 정지 안내. 정지 중이 아니면 null */
    @Transactional(readOnly = true)
    public UserResponse.Suspension currentSuspension(User user) {
        if (user.getStatus() != UserStatus.SUSPENDED) {
            return null;
        }
        return suspensionRepository.findFirstByUserIdOrderByIdDesc(user.getId())
                .map(s -> new UserResponse.Suspension(s.getReason(), s.getEndsAt()))
                .orElse(null);
    }
}
