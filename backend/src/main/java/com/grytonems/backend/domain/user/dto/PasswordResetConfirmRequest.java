package com.grytonems.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /api/auth/password-reset/confirm (AUTH-05) */
public record PasswordResetConfirmRequest(

        @NotBlank(message = "링크가 올바르지 않아요")
        String token,

        @NotBlank(message = "비밀번호를 입력해 주세요")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "비밀번호는 영문과 숫자를 포함해 8자 이상으로 입력해 주세요")
        String newPassword
) {
}
