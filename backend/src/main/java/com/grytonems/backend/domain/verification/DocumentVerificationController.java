package com.grytonems.backend.domain.verification;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.grytonems.backend.domain.verification.dto.AdminVerificationResponse;
import com.grytonems.backend.domain.verification.dto.DocumentSubmitResponse;
import com.grytonems.backend.domain.verification.dto.MyVerificationResponse;
import com.grytonems.backend.domain.verification.dto.RejectVerificationRequest;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 서류 인증 (AUTH-07), 내 인증 상태, 관리자 서류 심사 (ADMN-03) */
@RestController
@RequiredArgsConstructor
public class DocumentVerificationController {

    private final DocumentVerificationService service;

    @PostMapping(value = "/api/verifications/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentSubmitResponse> submit(@AuthenticationPrincipal AuthUser loginUser,
            @RequestParam Long schoolId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(service.submit(loginUser.id(), schoolId, file));
    }

    @GetMapping("/api/verifications/me")
    public ApiResponse<MyVerificationResponse> getMine(@AuthenticationPrincipal AuthUser loginUser) {
        return ApiResponse.ok(service.getMine(loginUser.id()));
    }

    @GetMapping("/api/admin/verifications")
    public ApiResponse<CursorPage<AdminVerificationResponse>> getList(
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.getList(status, cursor, size));
    }

    /** 서류 사진 (관리자만). 화면에서는 Authorization 헤더를 붙여 받아 blob으로 보여줍니다 */
    @GetMapping("/api/admin/verifications/{verificationId}/document")
    public ResponseEntity<byte[]> getDocument(@PathVariable Long verificationId) {
        DocumentVerificationService.DocumentFile file = service.getDocument(verificationId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.bytes());
    }

    @PatchMapping("/api/admin/verifications/{verificationId}/approve")
    public ApiResponse<AdminVerificationResponse> approve(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long verificationId) {
        return ApiResponse.ok(service.approve(loginUser.id(), verificationId));
    }

    @PatchMapping("/api/admin/verifications/{verificationId}/reject")
    public ApiResponse<AdminVerificationResponse> reject(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long verificationId, @Valid @RequestBody RejectVerificationRequest request) {
        return ApiResponse.ok(service.reject(loginUser.id(), verificationId, request.reason()));
    }
}
