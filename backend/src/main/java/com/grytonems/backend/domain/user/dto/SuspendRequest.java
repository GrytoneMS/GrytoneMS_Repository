package com.grytonems.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/admin/users/{userId}/suspension (ADMN-04)
 * days: 7 / 30 / null(영구). 그 밖의 값은 INVALID_INPUT
 */
public record SuspendRequest(
        Integer days,
        @NotBlank(message = "정지 사유를 입력해 주세요")
        @Size(max = 200, message = "정지 사유는 200자 이하로 입력해 주세요")
        String reason,
        Long reportId
) {
}
