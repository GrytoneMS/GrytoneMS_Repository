package com.grytonems.backend.domain.verification.dto;

import com.grytonems.backend.domain.verification.VerificationStatus;

/** POST /api/verifications/document (AUTH-07) */
public record DocumentSubmitResponse(Long verificationId, VerificationStatus status) {
}
