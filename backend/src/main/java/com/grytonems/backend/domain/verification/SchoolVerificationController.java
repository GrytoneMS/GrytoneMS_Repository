package com.grytonems.backend.domain.verification;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.user.dto.UserResponse;
import com.grytonems.backend.domain.verification.dto.SchoolEmailConfirmRequest;
import com.grytonems.backend.domain.verification.dto.SchoolEmailSendRequest;
import com.grytonems.backend.domain.verification.dto.SchoolEmailSendResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AUTH-06 학교 이메일 인증 (로그인 필요) */
@RestController
@RequiredArgsConstructor
public class SchoolVerificationController {

    private final SchoolVerificationService verificationService;

    /** 인증 코드 받기 */
    @PostMapping("/api/verifications/school-email")
    public ApiResponse<SchoolEmailSendResponse> sendCode(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody SchoolEmailSendRequest request) {
        return ApiResponse.ok(verificationService.sendCode(loginUser.id(), request));
    }

    /** 코드 확인 → 인증 회원 */
    @PostMapping("/api/verifications/school-email/confirm")
    public ApiResponse<UserResponse> confirm(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody SchoolEmailConfirmRequest request) {
        return ApiResponse.ok(verificationService.confirm(loginUser.id(), request));
    }
}
