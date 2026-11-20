package com.grytonems.backend.domain.application;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.application.dto.AcceptResponse;
import com.grytonems.backend.domain.application.dto.ApplyRequest;
import com.grytonems.backend.domain.application.dto.ApplyResponse;
import com.grytonems.backend.domain.application.dto.CancelResponse;
import com.grytonems.backend.domain.application.dto.ContactResponse;
import com.grytonems.backend.domain.application.dto.LeaveResponse;
import com.grytonems.backend.domain.application.dto.MyApplicationResponse;
import com.grytonems.backend.domain.application.dto.RejectRequest;
import com.grytonems.backend.domain.application.dto.RejectResponse;
import com.grytonems.backend.domain.application.dto.RoleApplicantsResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 지원 API (API 명세서 "지원") */
@RestController
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    /** APPL-01 지원하기 (학교 인증 회원만: SecurityConfig) */
    @PostMapping("/api/posts/{postId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApplyResponse> apply(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long postId, @Valid @RequestBody ApplyRequest request) {
        return ApiResponse.ok(applicationService.apply(loginUser.id(), postId, request));
    }

    /** APPL-04 지원자 목록 (팀장만) */
    @GetMapping("/api/posts/{postId}/applications")
    public ApiResponse<List<RoleApplicantsResponse>> getApplicants(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long postId) {
        return ApiResponse.ok(applicationService.getApplicants(loginUser.id(), postId));
    }

    /** APPL-05 수락 (팀장만) */
    @PatchMapping("/api/applications/{applicationId}/accept")
    public ApiResponse<AcceptResponse> accept(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long applicationId) {
        return ApiResponse.ok(applicationService.accept(loginUser.id(), applicationId));
    }

    /** APPL-06 거절 (팀장만). 본문은 없어도 됩니다 */
    @PatchMapping("/api/applications/{applicationId}/reject")
    public ApiResponse<RejectResponse> reject(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long applicationId, @RequestBody(required = false) RejectRequest request) {
        return ApiResponse.ok(applicationService.reject(loginUser.id(), applicationId, request));
    }

    /** APPL-09 팀 나가기(지원자 본인)·내보내기(팀장) */
    @PatchMapping("/api/applications/{applicationId}/leave")
    public ApiResponse<LeaveResponse> leave(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long applicationId) {
        return ApiResponse.ok(applicationService.leave(loginUser.id(), applicationId));
    }

    /** APPL-03 지원 취소 (대기 상태만) */
    @PatchMapping("/api/applications/{applicationId}/cancel")
    public ApiResponse<CancelResponse> cancel(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long applicationId) {
        return ApiResponse.ok(applicationService.cancel(loginUser.id(), applicationId));
    }

    /** APPL-07 연락 수단 보기 (팀장·수락된 지원자만) */
    @GetMapping("/api/posts/{postId}/contact")
    public ApiResponse<ContactResponse> getContact(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long postId) {
        return ApiResponse.ok(applicationService.getContact(loginUser.id(), postId));
    }

    /** APPL-10 내 지원 내역 */
    @GetMapping("/api/users/me/applications")
    public ApiResponse<CursorPage<MyApplicationResponse>> getMyApplications(
            @AuthenticationPrincipal AuthUser loginUser,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(applicationService.getMyApplications(loginUser.id(), status, cursor, size));
    }
}
