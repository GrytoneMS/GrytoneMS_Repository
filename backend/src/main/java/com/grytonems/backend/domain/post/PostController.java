package com.grytonems.backend.domain.post;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import com.grytonems.backend.domain.post.dto.PostDetailResponse;
import com.grytonems.backend.domain.post.dto.PostCardResponse;
import com.grytonems.backend.domain.post.dto.PostCreateRequest;
import com.grytonems.backend.domain.post.dto.PostCreateResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 모집글 API (API 명세서 모집글)
 */
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** POST-01: 모집글 작성 (학교 인증 회원만. SecurityConfig에서 막아 줌) */
    @PostMapping("/api/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostCreateResponse> createPost(
            @AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.ok(postService.createPost(loginUser.id(), request));
    }

    /** LIST-01·02: 모집글 목록 (비회원도 가능) */
    @GetMapping("/api/posts")
    public ApiResponse<CursorPage<PostCardResponse>> getPosts(
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(postService.getPosts(sort, cursor, size));
    }

    /** LIST-05: 모집글 상세 (비회원도 가능) */
    @GetMapping("/api/posts/{postId}")
    public ApiResponse<PostDetailResponse> getPost(
            @AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long postId) {
        Long loginUserId = (loginUser == null) ? null : loginUser.id();
        return ApiResponse.ok(postService.getPost(postId, loginUserId));
    }
}