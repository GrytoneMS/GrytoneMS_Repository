package com.grytonems.backend.domain.school.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH /api/admin/schools/{schoolId} (ADMN-05): 바꿀 값만 보냅니다 (null이면 그대로) */
public record SchoolUpdateRequest(

        @Size(min = 1, max = 50, message = "학교명은 50자 이내로 입력해 주세요")
        String name,

        @Size(max = 30, message = "캠퍼스명은 30자 이내로 입력해 주세요")
        String campus,

        @Size(max = 100, message = "도메인은 100자 이내로 입력해 주세요")
        @Pattern(regexp = "^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "이메일 도메인 형식이 아니에요 (예: dankook.ac.kr)")
        String emailDomain,

        Boolean active
) {
}
