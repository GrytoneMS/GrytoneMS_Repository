package com.grytonems.backend.domain.profile.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/users/me/skills (PROF-03). 보낸 목록으로 통째로 바꿉니다.
 * 중복(대소문자만 다른 것 포함)은 서버가 자동으로 하나로 합치고, 합친 뒤 10개를 넘으면 오류입니다.
 */
public record SkillsUpdateRequest(

        @NotNull(message = "기술 목록을 보내 주세요")
        @Size(max = 30, message = "기술·툴은 최대 10개까지 등록할 수 있어요")
        List<@NotBlank(message = "기술 이름이 비어 있어요")
             @Size(max = 30, message = "기술 이름은 30자 이하로 입력해 주세요") String> skills
) {
}
