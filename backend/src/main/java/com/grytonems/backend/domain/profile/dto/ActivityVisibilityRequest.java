package com.grytonems.backend.domain.profile.dto;

import jakarta.validation.constraints.NotNull;

/** PUT /api/users/me/activity-visibility (PROF-08) */
public record ActivityVisibilityRequest(

        @NotNull(message = "공개 여부를 골라 주세요")
        Boolean showActivity
) {
}
