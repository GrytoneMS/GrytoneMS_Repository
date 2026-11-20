package com.grytonems.backend.domain.post;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.post.dto.MyPostResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

/** APPL-11 내 모집글 (마이페이지) */
@RestController
@RequiredArgsConstructor
public class MyPostController {

    private final MyPostService myPostService;

    /** status 없으면 전체, OPEN = 모집중 탭, CLOSED = 마감 탭 */
    @GetMapping("/api/users/me/posts")
    public ApiResponse<CursorPage<MyPostResponse>> getMyPosts(@AuthenticationPrincipal AuthUser loginUser,
            @RequestParam(required = false) RecruitStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(myPostService.getMyPosts(loginUser.id(), status, cursor, size));
    }
}
