package com.grytonems.backend.domain.user.dto;

/** 로그인·회원가입 응답. accessToken은 이후 요청에 Authorization: Bearer {accessToken} 으로 보냅니다. */
public record LoginResponse(String accessToken, UserResponse user) {
}
