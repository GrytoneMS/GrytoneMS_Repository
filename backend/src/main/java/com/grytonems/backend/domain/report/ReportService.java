package com.grytonems.backend.domain.report;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.application.Application;
import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.notification.NotificationService;
import com.grytonems.backend.domain.notification.NotificationType;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostModerationService;
import com.grytonems.backend.domain.post.PostRepository;
import com.grytonems.backend.domain.report.dto.AdminReportResponse;
import com.grytonems.backend.domain.report.dto.ReportCreateRequest;
import com.grytonems.backend.domain.report.dto.ReportCreateResponse;
import com.grytonems.backend.domain.report.dto.ReportResolveResponse;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * SAFE-01 신고, SAFE-02 신고 3건 자동 숨김, ADMN-02 관리자 신고 처리
 *
 * - 신고는 학교 인증 회원만 (가입만 한 계정 여러 개로 남의 글을 숨기지 못하게)
 * - 모집글은 미처리 신고가 3건 쌓이면 PostModerationService.hide로 숨김
 * - 관리자는 신고 하나를 처리하면 같은 대상의 미처리 신고가 모두 함께 처리되고, 신고자 모두에게 알림
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    /** SAFE-02 자동 숨김 기준 */
    public static final int AUTO_HIDE_THRESHOLD = 3;

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ApplicationRepository applicationRepository;
    private final PostModerationService postModerationService;
    private final NotificationService notificationService;

    // ===== SAFE-01 신고 =====

    public ReportCreateResponse report(Long reporterId, ReportCreateRequest request) {
        checkTarget(reporterId, request.targetType(), request.targetId());
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, request.targetType(), request.targetId())) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Report report;
        try {
            report = reportRepository.saveAndFlush(Report.builder()
                    .reporter(reporter)
                    .targetType(request.targetType())
                    .targetId(request.targetId())
                    .reasonCode(request.reasonCode())
                    .detail(trimToNull(request.detail()))
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 신고 버튼을 동시에 두 번 누른 경우: UQ가 막아 줍니다
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        // SAFE-02: 모집글에 미처리 신고가 3건 쌓이면 관리자 확인 전까지 숨김
        if (request.targetType() == ReportTargetType.POST) {
            long pending = reportRepository.countByTargetTypeAndTargetIdAndStatus(
                    ReportTargetType.POST, request.targetId(), ReportStatus.RECEIVED);
            if (pending >= AUTO_HIDE_THRESHOLD) {
                postRepository.findById(request.targetId())
                        .filter(post -> !post.isHidden())
                        .ifPresent(post -> postModerationService.hide(post.getId()));
            }
        }
        return new ReportCreateResponse(report.getId(), ReportStatus.RECEIVED);
    }

    /** 신고할 수 있는 대상인지: 있어야 하고, 내 것이 아니어야 하고, 지원 메시지는 그 글의 팀장만 볼 수 있으므로 팀장만 */
    private void checkTarget(Long reporterId, ReportTargetType type, Long targetId) {
        switch (type) {
            case POST -> {
                Post post = postRepository.findById(targetId)
                        .filter(p -> p.getDeletedAt() == null && !p.isHidden())
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "모집글을 찾을 수 없어요"));
                if (post.getLeaderId().equals(reporterId)) {
                    throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
                }
            }
            case USER -> {
                User user = userRepository.findById(targetId)
                        .filter(u -> u.getDeletedAt() == null)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요"));
                if (user.getId().equals(reporterId)) {
                    throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
                }
            }
            case APPLICATION -> {
                Application application = applicationRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "지원 내역을 찾을 수 없어요"));
                if (application.getApplicant().getId().equals(reporterId)) {
                    throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
                }
                if (!application.getPost().getLeaderId().equals(reporterId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "내 모집글에 온 지원만 신고할 수 있어요");
                }
            }
        }
    }

    // ===== ADMN-02 관리자 신고 처리 =====

    @Transactional(readOnly = true)
    public CursorPage<AdminReportResponse> getReports(ReportStatus status, Long cursor, Integer size) {
        int pageSize = CursorPage.normalizeSize(size);
        List<Report> fetched = reportRepository.findForAdmin(status, cursor, PageRequest.of(0, pageSize + 1));
        // 관리자 화면은 한 번에 최대 50건이라 대상마다 따로 읽어도 충분합니다
        return CursorPage.of(fetched, pageSize, Report::getId, report -> AdminReportResponse.of(report,
                targetOf(report.getTargetType(), report.getTargetId()),
                reportRepository.countByTargetTypeAndTargetIdAndStatus(
                        report.getTargetType(), report.getTargetId(), ReportStatus.RECEIVED)));
    }

    private AdminReportResponse.Target targetOf(ReportTargetType type, Long targetId) {
        return switch (type) {
            case POST -> postRepository.findById(targetId)
                    .map(post -> new AdminReportResponse.Target(post.getTitle(), post.getDescription(),
                            post.getLeaderId(), post.isHidden(), post.getDeletedAt() != null))
                    .orElse(null);
            case USER -> userRepository.findById(targetId)
                    .map(user -> new AdminReportResponse.Target(user.getNickname(), user.getBio(),
                            user.getId(), false, user.getDeletedAt() != null))
                    .orElse(null);
            case APPLICATION -> applicationRepository.findById(targetId)
                    .map(application -> new AdminReportResponse.Target(application.getApplicant().getNickname(),
                            application.getMessage(), application.getApplicant().getId(), false, false))
                    .orElse(null);
        };
    }

    /**
     * 신고 처리: 대상에 조치를 하고, 같은 대상의 미처리 신고를 모두 처리완료로 바꾸고, 신고자들에게 알림.
     * 1차 조치: 모집글 = 문제없음(숨겨져 있으면 복구) / 숨김, 사용자·지원 메시지 = 문제없음
     */
    public ReportResolveResponse resolve(Long adminId, Long reportId, ReportAction action) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "신고를 찾을 수 없어요"));
        if (report.getStatus() == ReportStatus.RESOLVED) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
        ReportTargetType targetType = report.getTargetType();
        Long targetId = report.getTargetId();
        checkActionSupported(targetType, action);

        // 알림 보낼 신고자를 먼저 기억 (아래 일괄 변경 뒤에는 RECEIVED가 사라짐)
        List<Long> reporterIds = reportRepository.findReporterIds(targetType, targetId, ReportStatus.RECEIVED);

        // 대상에 조치 (삭제된 글이면 조치 없이 신고만 처리)
        if (targetType == ReportTargetType.POST) {
            postRepository.findById(targetId)
                    .filter(post -> post.getDeletedAt() == null)
                    .ifPresent(post -> {
                        if (action == ReportAction.NONE && post.isHidden()) {
                            postModerationService.restore(post.getId());   // SAFE-02 문제없음 → 복구
                        } else if (action == ReportAction.HIDE && !post.isHidden()) {
                            postModerationService.hide(post.getId());
                        }
                    });
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        int resolved = reportRepository.resolveAll(targetType, targetId, action, admin, LocalDateTime.now(),
                ReportStatus.RECEIVED, ReportStatus.RESOLVED);

        // 처리 결과 알림: 무슨 조치를 했는지는 알리지 않습니다 (명세 문구 그대로)
        notificationService.notifyAll(reporterIds, NotificationType.REPORT_RESOLVED, null, null,
                "신고하신 내용이 처리되었어요");
        return new ReportResolveResponse(reportId, action, resolved);
    }

    private void checkActionSupported(ReportTargetType targetType, ReportAction action) {
        boolean supported = action == ReportAction.NONE
                || (targetType == ReportTargetType.POST && action == ReportAction.HIDE);
        if (!supported) {
            throw new BusinessException(ErrorCode.REPORT_ACTION_NOT_SUPPORTED);
        }
    }

    private static String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
