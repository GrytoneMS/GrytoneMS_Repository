package com.grytonems.backend.domain.post.dto;

import java.time.LocalDate;
import java.util.List;

import com.grytonems.backend.domain.post.LocationCode;
import com.grytonems.backend.domain.post.MeetingType;
import com.grytonems.backend.domain.post.PostCategory;
import com.grytonems.backend.domain.post.RoleCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST-01 모집글 작성 요청 (API 명세서 POST /api/posts)
 * 여러 칸을 함께 봐야 하는 규칙(장소·행사 필수 여부, 날짜 순서)은 Service에서 검사합니다.
 */
public record PostCreateRequest(

        @NotBlank(message = "제목을 입력해 주세요")
        @Size(min = 5, max = 40, message = "제목은 5~40자로 입력해 주세요")
        String title,

        @NotNull(message = "모집 유형을 선택해 주세요")
        PostCategory category,

        @NotBlank(message = "소개를 입력해 주세요")
        @Size(min = 20, max = 1000, message = "소개는 20~1,000자로 입력해 주세요")
        String description,

        @NotNull(message = "활동 시작일을 입력해 주세요")
        LocalDate periodStart,

        @NotNull(message = "활동 종료일을 입력해 주세요")
        LocalDate periodEnd,

        @NotNull(message = "모집 마감일을 입력해 주세요")
        LocalDate deadline,

        @NotNull(message = "진행 방식을 선택해 주세요")
        MeetingType meetingType,

        LocationCode locationCode,

        @Size(max = 100, message = "장소는 100자 이하로 입력해 주세요")
        String locationText,

        @Size(max = 30, message = "모임 빈도는 30자 이하로 입력해 주세요")
        String meetingFreq,

        @NotEmpty(message = "모집 역할을 1개 이상 추가해 주세요")
        @Size(max = 5, message = "모집 역할은 최대 5개까지 추가할 수 있어요")
        List<@Valid RoleRequest> roles,

        @Valid
        EventRequest event,

        @NotBlank(message = "연락 링크를 입력해 주세요")
        @Size(max = 500, message = "연락 링크는 500자 이하로 입력해 주세요")
        String contactLink
) {

    /** 역할 하나 */
    public record RoleRequest(
            @NotNull(message = "역할을 선택해 주세요")
            RoleCode roleCode,

            @NotBlank(message = "역할 이름을 입력해 주세요")
            @Size(max = 30, message = "역할 이름은 30자 이하로 입력해 주세요")
            String roleName,

            @Min(value = 1, message = "모집 인원은 1~5명이에요")
            @Max(value = 5, message = "모집 인원은 1~5명이에요")
            int headcount,

            @Size(max = 100, message = "요구 조건은 100자 이하로 입력해 주세요")
            String requirements
    ) {
    }

    /** 공모전·대회 행사 정보 (공모전·대회일 때만) */
    public record EventRequest(
            @NotBlank(message = "행사명을 입력해 주세요")
            @Size(max = 100, message = "행사명은 100자 이하로 입력해 주세요")
            String name,

            @NotBlank(message = "주최를 입력해 주세요")
            @Size(max = 100, message = "주최는 100자 이하로 입력해 주세요")
            String host,

            @NotBlank(message = "공식 링크를 입력해 주세요")
            @Size(max = 500, message = "공식 링크는 500자 이하로 입력해 주세요")
            String officialUrl,

            @NotNull(message = "접수 마감일을 입력해 주세요")
            LocalDate applyDeadline
    ) {
    }
}