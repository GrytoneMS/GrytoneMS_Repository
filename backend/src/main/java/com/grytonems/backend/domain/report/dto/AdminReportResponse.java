package com.grytonems.backend.domain.report.dto;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.report.Report;
import com.grytonems.backend.domain.report.ReportAction;
import com.grytonems.backend.domain.report.ReportReason;
import com.grytonems.backend.domain.report.ReportStatus;
import com.grytonems.backend.domain.report.ReportTargetType;

/**
 * GET /api/admin/reports 항목 (ADMN-02: 신고 목록, 대상 내용 확인)
 * pendingCountForTarget: 같은 대상에 아직 처리 안 된 신고 수 (3 이상이면 자동 숨김된 상태)
 */
public record AdminReportResponse(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        Target target,
        ReportReason reasonCode,
        String detail,
        Reporter reporter,
        ReportStatus status,
        ReportAction action,
        long pendingCountForTarget,
        LocalDateTime createdAt,
        LocalDateTime handledAt
) {

    /**
     * 신고 대상 내용. 종류에 따라 채워지는 칸이 다릅니다.
     * POST: title·content(소개)·authorId·hidden·deleted / USER: title(닉네임)·content(한 줄 소개)·deleted
     * APPLICATION: title(지원자 닉네임)·content(지원 메시지)·authorId(지원자 id)
     * 대상을 찾을 수 없으면 null
     */
    public record Target(String title, String content, Long authorId, boolean hidden, boolean deleted) {
    }

    public record Reporter(Long id, String nickname) {
    }

    public static AdminReportResponse of(Report report, Target target, long pendingCountForTarget) {
        return new AdminReportResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                target,
                report.getReasonCode(),
                report.getDetail(),
                new Reporter(report.getReporter().getId(), report.getReporter().getNickname()),
                report.getStatus(),
                report.getAction(),
                pendingCountForTarget,
                report.getCreatedAt(),
                report.getHandledAt()
        );
    }
}
