package com.grytonems.backend.domain.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/posts/{postId}/applications (APPL-01) */
public record ApplyRequest(

        @NotNull(message = "지원할 역할을 선택해 주세요")
        Long postRoleId,

        @NotBlank(message = "지원 메시지를 입력해 주세요")
        @Size(min = 20, max = 300, message = "지원 메시지는 20~300자로 입력해 주세요")
        String message,

        @NotBlank(message = "가능한 시간을 입력해 주세요")
        @Size(max = 100, message = "가능한 시간은 100자 이하로 입력해 주세요")
        String availableTime
) {
}
