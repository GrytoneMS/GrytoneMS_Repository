package com.grytonems.backend.domain.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PATCH /api/admin/verifications/{id}/reject */
public record RejectVerificationRequest(

        @NotBlank(message = "반려 사유를 입력해 주세요")
        @Size(max = 200, message = "반려 사유는 200자 이내로 입력해 주세요")
        String reason
) {
}
