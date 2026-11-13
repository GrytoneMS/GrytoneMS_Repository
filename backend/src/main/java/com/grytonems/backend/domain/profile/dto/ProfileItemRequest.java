package com.grytonems.backend.domain.profile.dto;

import java.time.LocalDate;

import com.grytonems.backend.domain.profile.ExperienceType;
import com.grytonems.backend.domain.profile.ItemVisibility;
import com.grytonems.backend.domain.profile.ProfileItemType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST·PUT /api/users/me/profile-items (PROF-04~07, 09).
 * itemType마다 필수 칸이 달라 서비스에서 한 번 더 확인합니다. 수정 때 itemType은 바꿀 수 없습니다.
 */
public record ProfileItemRequest(

        @NotNull(message = "항목 종류를 골라 주세요")
        ProfileItemType itemType,

        ExperienceType subType,

        @NotBlank(message = "이름을 입력해 주세요")
        @Size(max = 100, message = "100자 이내로 입력해 주세요")
        String title,

        @Size(max = 50, message = "역할은 50자 이내로 입력해 주세요")
        String role,

        @Size(max = 50, message = "50자 이내로 입력해 주세요")
        String issuer,

        LocalDate startDate,

        LocalDate endDate,

        @Size(max = 300, message = "설명은 300자 이내로 입력해 주세요")
        String description,

        @Size(max = 500, message = "링크는 500자 이내로 입력해 주세요")
        String url,

        ItemVisibility visibility
) {
}
