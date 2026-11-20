package com.grytonems.backend.domain.verification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/verifications/school-email */
public record SchoolEmailSendRequest(

        @NotNull(message = "학교를 선택해 주세요")
        Long schoolId,

        @NotBlank(message = "학교 이메일을 입력해 주세요")
        @Email(message = "이메일 형식이 아니에요")
        @Size(max = 255, message = "이메일이 너무 길어요")
        String schoolEmail
) {
}
