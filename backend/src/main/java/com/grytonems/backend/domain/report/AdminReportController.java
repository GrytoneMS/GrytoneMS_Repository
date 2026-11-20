package com.grytonems.backend.domain.report;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.report.dto.AdminReportResponse;
import com.grytonems.backend.domain.report.dto.ReportResolveRequest;
import com.grytonems.backend.domain.report.dto.ReportResolveResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** ADMN-02 관리자 신고 처리 (/api/admin/** 은 관리자만) */
@RestController
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    /** status: RECEIVED(접수) / RESOLVED(처리완료), 없으면 전체 */
    @GetMapping("/api/admin/reports")
    public ApiResponse<CursorPage<AdminReportResponse>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(reportService.getReports(status, cursor, size));
    }

    @PatchMapping("/api/admin/reports/{reportId}/resolve")
    public ApiResponse<ReportResolveResponse> resolve(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long reportId, @Valid @RequestBody ReportResolveRequest request) {
        return ApiResponse.ok(reportService.resolve(loginUser.id(), reportId, request.action(),
                request.suspendDays(), request.suspendReason()));
    }
}
