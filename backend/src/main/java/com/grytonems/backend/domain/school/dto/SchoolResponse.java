package com.grytonems.backend.domain.school.dto;

import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;

/**
 * [3] Response DTO: 프론트에 내려줄 값만 골라 담습니다.
 * Entity를 그대로 반환하지 않습니다 (불필요한 컬럼·개인정보 노출 방지).
 */
public record SchoolResponse(Long id, String name, String campus, String emailDomain, Region region) {

    public static SchoolResponse from(School school) {
        return new SchoolResponse(
                school.getId(),
                school.getName(),
                school.getCampus(),
                school.getEmailDomain(),
                school.getRegion()
        );
    }
}
