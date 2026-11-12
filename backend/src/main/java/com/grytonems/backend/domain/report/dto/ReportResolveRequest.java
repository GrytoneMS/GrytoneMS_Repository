package com.grytonems.backend.domain.report.dto;

import com.grytonems.backend.domain.report.ReportAction;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/admin/reports/{id}/resolve (ADMN-02) */
public record ReportResolveRequest(@NotNull(message = "조치를 선택해 주세요") ReportAction action) {
}
