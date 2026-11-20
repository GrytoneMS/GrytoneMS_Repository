package com.grytonems.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/login (AUTH-04) */
public record LoginRequest(

        @NotBlank(message = "이메일을 입력해 주세요")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요")
        String password
) {
}
