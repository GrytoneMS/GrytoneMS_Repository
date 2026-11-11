package com.grytonems.backend.domain.application.dto;

import com.grytonems.backend.domain.application.ApplicationStatus;

/**
 * PATCH /api/applications/{id}/accept (APPL-05).
 * roleClosed: 이번 수락으로 역할 인원이 다 차서 마감됐는지
 * postClosed: 모든 역할이 마감돼 글까지 마감됐는지
 */
public record AcceptResponse(Long applicationId, ApplicationStatus status, boolean roleClosed, boolean postClosed) {
}
