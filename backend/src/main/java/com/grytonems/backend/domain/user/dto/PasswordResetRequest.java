package com.grytonems.backend.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/password-reset (AUTH-05) */
public record PasswordResetRequest(

        @NotBlank(message = "이메일을 입력해 주세요")
        @Email(message = "이메일 형식이 아니에요")
        String email
) {
}
