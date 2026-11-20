package com.grytonems.backend.domain.profile.dto;

import java.time.LocalDate;

import com.grytonems.backend.domain.profile.ExperienceType;
import com.grytonems.backend.domain.profile.ItemVisibility;
import com.grytonems.backend.domain.profile.ProfileItem;
import com.grytonems.backend.domain.profile.ProfileItemType;

public record ProfileItemResponse(
        Long id,
        ProfileItemType itemType,
        ExperienceType subType,
        String title,
        String role,
        String issuer,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        String url,
        ItemVisibility visibility
) {

    public static ProfileItemResponse from(ProfileItem item) {
        return new ProfileItemResponse(item.getId(), item.getItemType(), item.getSubType(), item.getTitle(),
                item.getRole(), item.getIssuer(), item.getStartDate(), item.getEndDate(), item.getDescription(),
                item.getUrl(), item.getVisibility());
    }
}
