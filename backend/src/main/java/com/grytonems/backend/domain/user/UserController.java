package com.grytonems.backend.domain.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.user.dto.NicknameChangeRequest;
import com.grytonems.backend.domain.user.dto.UserResponse;
import com.grytonems.backend.domain.user.dto.WithdrawRequest;
import com.grytonems.backend.domain.user.dto.WithdrawalCheckResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountService accountService;

    /** 내 정보 (새로고침 후 로그인 상태·권한 확인용) */
    @GetMapping("/api/users/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthUser loginUser) {
        return ApiResponse.ok(userService.getMe(loginUser.id()));
    }

    /** AUTH-03 닉네임 변경 (30일에 한 번) */
    @PatchMapping("/api/users/me/nickname")
    public ApiResponse<UserResponse> changeNickname(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody NicknameChangeRequest request) {
        return ApiResponse.ok(accountService.changeNickname(loginUser.id(), request.nickname()));
    }

    /** AUTH-09 탈퇴 전 확인: 모집 중인 글·참여 중인 팀 수 */
    @GetMapping("/api/users/me/withdrawal-check")
    public ApiResponse<WithdrawalCheckResponse> withdrawalCheck(@AuthenticationPrincipal AuthUser loginUser) {
        return ApiResponse.ok(accountService.withdrawalCheck(loginUser.id()));
    }

    /** AUTH-09 회원 탈퇴 */
    @DeleteMapping("/api/users/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody WithdrawRequest request) {
        accountService.withdraw(loginUser.id(), request.password());
        return ApiResponse.ok();
    }
}
