package com.grytonems.backend.domain.report.dto;

import com.grytonems.backend.domain.report.ReportStatus;

/** 신고 접수 결과. 신고 3건이 쌓여 숨겨졌는지는 신고자에게 알려주지 않습니다 */
public record ReportCreateResponse(Long reportId, ReportStatus status) {
}
