package com.grytonems.backend.domain.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.user.dto.LoginRequest;
import com.grytonems.backend.domain.user.dto.LoginResponse;
import com.grytonems.backend.domain.user.dto.SignupRequest;
import com.grytonems.backend.global.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** AUTH-01 회원가입 */
    @PostMapping("/api/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LoginResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    /** AUTH-04 로그인 */
    @PostMapping("/api/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * AUTH-04 로그아웃.
     * 토큰은 서버에 저장하지 않으므로 프론트에서 저장한 토큰을 지우면 됩니다.
     * (앱 푸시 토큰 삭제는 1차 범위에서 제외)
     */
    @PostMapping("/api/auth/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }
}
