package com.grytonems.backend.domain.post;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.post.dto.PostCreateRequest;
import com.grytonems.backend.domain.post.dto.PostCreateResponse;
import com.grytonems.backend.global.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 모집글 API (API 명세서 모집글)
 */
@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * POST-01: 모집글 작성
     * TODO: 로그인 기능이 생기면 X-User-Id 헤더 대신 로그인한 사용자 id를 쓰도록 바꾸기
     */
    @PostMapping("/api/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostCreateResponse> createPost(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.ok(postService.createPost(userId, request));
    }
}