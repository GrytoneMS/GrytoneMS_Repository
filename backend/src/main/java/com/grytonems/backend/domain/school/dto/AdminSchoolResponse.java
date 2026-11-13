package com.grytonems.backend.domain.school.dto;

import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;

/** 관리자용 학교 정보 (활성 여부 포함) */
public record AdminSchoolResponse(Long id, String name, String campus, String emailDomain, Region region,
        boolean active) {

    public static AdminSchoolResponse from(School school) {
        return new AdminSchoolResponse(school.getId(), school.getName(), school.getCampus(), school.getEmailDomain(),
                school.getRegion(), school.isActive());
    }
}
