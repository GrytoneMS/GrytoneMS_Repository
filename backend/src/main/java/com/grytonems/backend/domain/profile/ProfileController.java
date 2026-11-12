package com.grytonems.backend.domain.profile;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.profile.dto.ProfileResponse;
import com.grytonems.backend.domain.profile.dto.ProfileUpdateRequest;
import com.grytonems.backend.domain.profile.dto.RoleTagsUpdateRequest;
import com.grytonems.backend.domain.profile.dto.SkillsUpdateRequest;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 프로필 API (로그인 필요). 수정 API는 모두 수정된 내 프로필 전체를 돌려줍니다 */
@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /** 내 프로필 */
    @GetMapping("/api/users/me/profile")
    public ApiResponse<ProfileResponse> getMyProfile(@AuthenticationPrincipal AuthUser loginUser) {
        return ApiResponse.ok(profileService.getProfile(loginUser.id()));
    }

    /** PROF-01 기본 정보 수정 */
    @PutMapping("/api/users/me/profile")
    public ApiResponse<ProfileResponse> updateMyProfile(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(profileService.updateProfile(loginUser.id(), request));
    }

    /** PROF-02 희망 역할 태그 교체 */
    @PutMapping("/api/users/me/role-tags")
    public ApiResponse<ProfileResponse> updateMyRoleTags(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody RoleTagsUpdateRequest request) {
        return ApiResponse.ok(profileService.updateRoleTags(loginUser.id(), request));
    }

    /** PROF-03 보유 기술·툴 교체 */
    @PutMapping("/api/users/me/skills")
    public ApiResponse<ProfileResponse> updateMySkills(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody SkillsUpdateRequest request) {
        return ApiResponse.ok(profileService.updateSkills(loginUser.id(), request));
    }

    /** PROF-12 타인 프로필 보기 */
    @GetMapping("/api/users/{userId}/profile")
    public ApiResponse<ProfileResponse> getProfile(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long userId) {
        return ApiResponse.ok(profileService.getOtherProfile(loginUser.id(), userId));
    }
}
