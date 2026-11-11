package com.grytonems.backend.domain.application.dto;

import com.grytonems.backend.domain.application.ApplicationStatus;

public record RejectResponse(Long applicationId, ApplicationStatus status) {
}
