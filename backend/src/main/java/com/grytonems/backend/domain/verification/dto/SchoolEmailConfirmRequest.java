package com.grytonems.backend.domain.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /api/verifications/school-email/confirm */
public record SchoolEmailConfirmRequest(

        @NotBlank(message = "인증 코드를 입력해 주세요")
        @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 숫자 6자리예요")
        String code
) {
}
