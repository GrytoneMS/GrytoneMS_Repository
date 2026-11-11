package com.grytonems.backend.domain.post;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.global.common.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자: 모집글 숨김·복구 (ADMN-02 조치 중 숨김)
 * /api/admin/** 은 SecurityConfig에서 관리자만 접근하도록 막혀 있습니다.
 */
@RestController
@RequiredArgsConstructor
public class AdminPostController {

    private final PostModerationService postModerationService;

    /** 숨김 */
    @PatchMapping("/api/admin/posts/{postId}/hide")
    public ApiResponse<Void> hide(@PathVariable Long postId) {
        postModerationService.hide(postId);
        return ApiResponse.ok();
    }

    /** 복구 */
    @PatchMapping("/api/admin/posts/{postId}/restore")
    public ApiResponse<Void> restore(@PathVariable Long postId) {
        postModerationService.restore(postId);
        return ApiResponse.ok();
    }
}
