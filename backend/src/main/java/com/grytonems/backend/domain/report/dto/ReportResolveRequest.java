package com.grytonems.backend.domain.report.dto;

import com.grytonems.backend.domain.report.ReportAction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/admin/reports/{id}/resolve (ADMN-02)
 * action이 SUSPEND일 때만 suspendDays(7 / 30 / null=영구), suspendReason(필수, 200자)을 씁니다.
 */
public record ReportResolveRequest(
        @NotNull(message = "조치를 선택해 주세요") ReportAction action,
        Integer suspendDays,
        @Size(max = 200, message = "정지 사유는 200자 이하로 입력해 주세요") String suspendReason
) {
}
