package com.grytonems.backend.domain.application.dto;

import com.grytonems.backend.domain.application.ApplicationStatus;

public record ApplyResponse(Long id, ApplicationStatus status) {
}
