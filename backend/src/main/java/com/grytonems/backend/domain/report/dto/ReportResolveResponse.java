package com.grytonems.backend.domain.report.dto;

import com.grytonems.backend.domain.report.ReportAction;

/** resolvedCount: 같은 대상에 쌓인 미처리 신고 중 이번에 함께 처리된 수 */
public record ReportResolveResponse(Long reportId, ReportAction action, int resolvedCount) {
}
