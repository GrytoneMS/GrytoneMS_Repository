package com.grytonems.backend.domain.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.user.dto.UserResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 내 정보 (새로고침 후 로그인 상태·권한 확인용) */
    @GetMapping("/api/users/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthUser loginUser) {
        return ApiResponse.ok(userService.getMe(loginUser.id()));
    }
}
