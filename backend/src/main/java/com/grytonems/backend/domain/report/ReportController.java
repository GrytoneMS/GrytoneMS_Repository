package com.grytonems.backend.domain.report;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.report.dto.ReportCreateRequest;
import com.grytonems.backend.domain.report.dto.ReportCreateResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** SAFE-01 신고 (학교 인증 회원만, SecurityConfig) */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportCreateResponse> report(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody ReportCreateRequest request) {
        return ApiResponse.ok(reportService.report(loginUser.id(), request));
    }
}
