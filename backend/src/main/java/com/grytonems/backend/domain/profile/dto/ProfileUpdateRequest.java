package com.grytonems.backend.domain.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/users/me/profile (PROF-01).
 * 세 항목을 통째로 바꿉니다. 비우거나 보내지 않은 항목은 지워집니다.
 */
public record ProfileUpdateRequest(

        @Size(max = 50, message = "학과는 50자 이하로 입력해 주세요")
        String major,

        @Min(value = 1, message = "학년은 1~6 사이로 선택해 주세요")
        @Max(value = 6, message = "학년은 1~6 사이로 선택해 주세요")
        Integer grade,

        @Size(max = 50, message = "한 줄 소개는 50자 이하로 입력해 주세요")
        String bio
) {
}
