package com.grytonems.backend.domain.application.dto;

import com.grytonems.backend.domain.application.ApplicationStatus;

/** PATCH /api/applications/{id}/leave (APPL-09). 자리가 생겨 역할·글이 다시 모집중이 됐는지 함께 알려줍니다 */
public record LeaveResponse(Long applicationId, ApplicationStatus status, boolean roleReopened, boolean postReopened) {
}
