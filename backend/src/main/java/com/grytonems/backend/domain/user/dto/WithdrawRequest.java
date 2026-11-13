package com.grytonems.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/** DELETE /api/users/me (AUTH-09) */
public record WithdrawRequest(

        @NotBlank(message = "비밀번호를 입력해 주세요")
        String password
) {
}
