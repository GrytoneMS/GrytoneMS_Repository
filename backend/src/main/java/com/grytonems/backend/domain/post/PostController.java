package com.grytonems.backend.domain.post;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.post.dto.PostCardResponse;
import com.grytonems.backend.domain.post.dto.PostCreateRequest;
import com.grytonems.backend.domain.post.dto.PostCreateResponse;
import com.grytonems.backend.domain.post.dto.PostDetailResponse;
import com.grytonems.backend.domain.post.dto.PostSearchCondition;
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

    /**
     * LIST-01~04: 모집글 목록 (비회원도 가능)
     * 여러 값은 같은 이름을 반복해서 보냅니다. 예: ?roleCode=DESIGN&roleCode=DEV
     */
    @GetMapping("/api/posts")
    public ApiResponse<CursorPage<PostCardResponse>> getPosts(
            @AuthenticationPrincipal AuthUser loginUser,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @RequestParam(name = "category", required = false) List<PostCategory> categories,
            @RequestParam(name = "roleCode", required = false) List<RoleCode> roleCodes,
            @RequestParam(name = "schoolId", required = false) List<Long> schoolIds,
            @RequestParam(name = "locationCode", required = false) List<LocationCode> locationCodes,
            @RequestParam(name = "meetingType", required = false) List<MeetingType> meetingTypes,
            @RequestParam(required = false) PostPeriod period,
            @RequestParam(required = false) Boolean openOnly,
            @RequestParam(required = false) String keyword) {
        PostSearchCondition condition = new PostSearchCondition(sort, cursor, size, categories, roleCodes,
                schoolIds, locationCodes, meetingTypes, period, openOnly, keyword);
        Long loginUserId = (loginUser == null) ? null : loginUser.id();
        return ApiResponse.ok(postService.getPosts(condition, loginUserId));
    }

    /** LIST-05: 모집글 상세 (비회원도 가능) */
    @GetMapping("/api/posts/{postId}")
    public ApiResponse<PostDetailResponse> getPost(
            @AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long postId) {
        Long loginUserId = (loginUser == null) ? null : loginUser.id();
        return ApiResponse.ok(postService.getPost(postId, loginUserId));
    }

    /** POST-04: 모집 수동 마감 (팀장만) */
    @PatchMapping("/api/posts/{postId}/close")
    public ApiResponse<Void> closePost(
            @AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long postId) {
        postService.closePost(loginUser.id(), postId);
        return ApiResponse.ok();
    }

    /** POST-03: 모집글 삭제 (팀장만, 지원자가 없을 때만) */
    @DeleteMapping("/api/posts/{postId}")
    public ApiResponse<Void> deletePost(
            @AuthenticationPrincipal AuthUser loginUser,
            @PathVariable Long postId) {
        postService.deletePost(loginUser.id(), postId);
        return ApiResponse.ok();
    }
}