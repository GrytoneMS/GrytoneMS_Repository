package com.grytonems.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** PATCH /api/users/me/nickname (AUTH-03) */
public record NicknameChangeRequest(

        @NotBlank(message = "닉네임을 입력해 주세요")
        @Pattern(regexp = "^\\s*[가-힣A-Za-z0-9]{2,12}\\s*$", message = "닉네임은 한글·영문·숫자 2~12자로 입력해 주세요")
        String nickname
) {
}
