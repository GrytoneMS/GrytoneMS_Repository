package com.grytonems.backend.domain.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.user.dto.SuspendRequest;
import com.grytonems.backend.domain.user.dto.SuspensionResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** ADMN-04 이용 정지·해제 (/api/admin/** 은 관리자만) */
@RestController
@RequiredArgsConstructor
public class AdminUserController {

    private final SuspensionService suspensionService;

    @PostMapping("/api/admin/users/{userId}/suspension")
    public ApiResponse<SuspensionResponse> suspend(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long userId, @Valid @RequestBody SuspendRequest request) {
        return ApiResponse.ok(suspensionService.suspend(loginUser.id(), userId, request.days(), request.reason(),
                request.reportId()));
    }

    @DeleteMapping("/api/admin/users/{userId}/suspension")
    public ApiResponse<SuspensionResponse> lift(@PathVariable Long userId) {
        return ApiResponse.ok(suspensionService.lift(userId));
    }
}
