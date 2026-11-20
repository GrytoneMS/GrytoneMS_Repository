package com.grytonems.backend.domain.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /api/auth/signup (AUTH-01) */
public record SignupRequest(

        @NotBlank(message = "이메일을 입력해 주세요")
        @Email(message = "이메일 형식이 아니에요")
        @Size(max = 255, message = "이메일이 너무 길어요")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                message = "비밀번호는 영문과 숫자를 포함해 8자 이상으로 입력해 주세요")
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요")
        @Size(min = 2, max = 12, message = "닉네임은 2~12자로 입력해 주세요")
        String nickname,

        @AssertTrue(message = "이용약관에 동의해 주세요")
        boolean agreeTerms,

        @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요")
        boolean agreePrivacy
) {
}
