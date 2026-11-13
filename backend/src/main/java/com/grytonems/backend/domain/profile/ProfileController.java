package com.grytonems.backend.domain.profile;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.grytonems.backend.domain.profile.dto.ActivityVisibilityRequest;
import com.grytonems.backend.domain.profile.dto.ProfileImageResponse;
import com.grytonems.backend.domain.profile.dto.ProfileItemRequest;
import com.grytonems.backend.domain.profile.dto.ProfileItemResponse;
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

    /** PROF-01 프로필 이미지 올리기 (jpg·png, 5MB 이하) */
    @PutMapping(value = "/api/users/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProfileImageResponse> changeImage(@AuthenticationPrincipal AuthUser loginUser,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(profileService.changeImage(loginUser.id(), file));
    }

    /** PROF-01 프로필 이미지 지우기 */
    @DeleteMapping("/api/users/me/profile-image")
    public ApiResponse<ProfileImageResponse> deleteImage(@AuthenticationPrincipal AuthUser loginUser) {
        return ApiResponse.ok(profileService.deleteImage(loginUser.id()));
    }

    /** PROF-04~07, 09 이력 항목 추가 */
    @PostMapping("/api/users/me/profile-items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProfileItemResponse> addItem(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody ProfileItemRequest request) {
        return ApiResponse.ok(profileService.addItem(loginUser.id(), request));
    }

    @PutMapping("/api/users/me/profile-items/{itemId}")
    public ApiResponse<ProfileItemResponse> updateItem(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long itemId, @Valid @RequestBody ProfileItemRequest request) {
        return ApiResponse.ok(profileService.updateItem(loginUser.id(), itemId, request));
    }

    @DeleteMapping("/api/users/me/profile-items/{itemId}")
    public ApiResponse<Void> deleteItem(@AuthenticationPrincipal AuthUser loginUser, @PathVariable Long itemId) {
        profileService.deleteItem(loginUser.id(), itemId);
        return ApiResponse.ok();
    }

    /** PROF-08 서비스 활동 이력 노출 여부 */
    @PutMapping("/api/users/me/activity-visibility")
    public ApiResponse<ProfileResponse> changeActivityVisibility(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody ActivityVisibilityRequest request) {
        return ApiResponse.ok(profileService.changeActivityVisibility(loginUser.id(), request.showActivity()));
    }

    /** APPL-02 지원자 프로필 (그 글의 팀장만) */
    @GetMapping("/api/applications/{applicationId}/profile")
    public ApiResponse<ProfileResponse> getApplicantProfile(@AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long applicationId) {
        return ApiResponse.ok(profileService.getApplicantProfile(loginUser.id(), applicationId));
    }
}
