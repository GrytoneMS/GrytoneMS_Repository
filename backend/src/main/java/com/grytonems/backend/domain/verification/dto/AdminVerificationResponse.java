package com.grytonems.backend.domain.verification.dto;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.verification.SchoolVerification;
import com.grytonems.backend.domain.verification.VerificationStatus;

/**
 * ADMN-03 서류 인증 목록 항목.
 * documentUrl: 관리자 전용 다운로드 주소(Authorization 헤더 필요). 승인·반려 뒤에는 사진이 삭제돼 null
 */
public record AdminVerificationResponse(
        Long id,
        UserSummary user,
        MyVerificationResponse.SchoolSummary school,
        VerificationStatus status,
        String documentUrl,
        String rejectReason,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt
) {

    public record UserSummary(Long id, String nickname, String email) {
    }

    public static AdminVerificationResponse from(SchoolVerification v) {
        return new AdminVerificationResponse(
                v.getId(),
                new UserSummary(v.getUser().getId(), v.getUser().getNickname(), v.getUser().getEmail()),
                MyVerificationResponse.SchoolSummary.from(v.getSchool()),
                v.getStatus(),
                v.getDocumentUrl() == null ? null : "/api/admin/verifications/" + v.getId() + "/document",
                v.getRejectReason(),
                v.getCreatedAt(),
                v.getReviewedAt());
    }
}
