package com.grytonems.backend.domain.application.dto;

import com.grytonems.backend.domain.application.ApplicationStatus;

/** PATCH /api/applications/{id}/cancel (APPL-03) */
public record CancelResponse(Long applicationId, ApplicationStatus status) {
}
