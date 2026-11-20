package com.grytonems.backend.domain.report.dto;

import com.grytonems.backend.domain.report.ReportReason;
import com.grytonems.backend.domain.report.ReportTargetType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/reports (SAFE-01)
 * targetId: POST면 모집글 id, USER면 사용자 id, APPLICATION이면 지원 id
 */
public record ReportCreateRequest(
        @NotNull(message = "신고 대상 종류를 선택해 주세요") ReportTargetType targetType,
        @NotNull(message = "신고 대상을 알 수 없어요") Long targetId,
        @NotNull(message = "신고 사유를 선택해 주세요") ReportReason reasonCode,
        @Size(max = 200, message = "상세 내용은 200자까지 쓸 수 있어요") String detail
) {
}
