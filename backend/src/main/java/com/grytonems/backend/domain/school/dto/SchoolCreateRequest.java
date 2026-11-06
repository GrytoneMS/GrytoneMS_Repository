package com.grytonems.backend.domain.school.dto;

import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * [3] Request DTO: 요청 본문 + 입력 검증 규칙.
 * 기능 명세서의 글자 수·형식 제한을 여기에 어노테이션으로 적습니다.
 * 검증에 실패하면 GlobalExceptionHandler가 fieldErrors로 바꿔 응답합니다.
 */
public record SchoolCreateRequest(

        @NotBlank(message = "학교명을 입력해 주세요")
        @Size(max = 50, message = "학교명은 50자 이하로 입력해 주세요")
        String name,

        @Size(max = 30, message = "캠퍼스명은 30자 이하로 입력해 주세요")
        String campus,

        @NotBlank(message = "이메일 도메인을 입력해 주세요")
        @Pattern(regexp = "^[a-z0-9.-]+\\.[a-z]{2,}$", message = "도메인 형식이 아니에요 (예: dankook.ac.kr)")
        String emailDomain,

        @NotNull(message = "지역을 선택해 주세요 (CHEONAN / ASAN)")
        Region region
) {
    public School toEntity() {
        return School.builder()
                .name(name)
                .campus(campus)
                .emailDomain(emailDomain)
                .region(region)
                .build();
    }
}
