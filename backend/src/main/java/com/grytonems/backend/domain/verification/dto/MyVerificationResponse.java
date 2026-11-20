package com.grytonems.backend.domain.verification.dto;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.verification.SchoolVerification;
import com.grytonems.backend.domain.verification.VerificationMethod;
import com.grytonems.backend.domain.verification.VerificationStatus;

/** GET /api/verifications/me: 가장 최근 학교 인증 신청 */
public record MyVerificationResponse(
        Long id,
        VerificationMethod method,
        VerificationStatus status,
        SchoolSummary school,
        String semester,
        String rejectReason,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt
) {

    public record SchoolSummary(Long id, String name, String campus) {
        public static SchoolSummary from(School school) {
            return new SchoolSummary(school.getId(), school.getName(), school.getCampus());
        }
    }

    public static MyVerificationResponse from(SchoolVerification v) {
        return new MyVerificationResponse(v.getId(), v.getMethod(), v.getStatus(), SchoolSummary.from(v.getSchool()),
                v.getSemester(), v.getRejectReason(), v.getCreatedAt(), v.getReviewedAt());
    }
}
