package com.grytonems.backend.domain.scrap;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.scrap.dto.MyScrapResponse;
import com.grytonems.backend.domain.scrap.dto.ScrapResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

/**
 * SCRP-01·02 스크랩.
 * 북마크 아이콘을 켜면 PUT, 끄면 DELETE (같은 요청을 여러 번 보내도 결과가 같습니다).
 * 스크랩은 학교 인증 회원만 (SecurityConfig). 해제와 내 스크랩 목록은 로그인만 하면 됩니다 (재인증 기간에도 정리할 수 있게).
 */
@RestController
@RequiredArgsConstructor
public class ScrapController {

    private final ScrapService scrapService;

    @PutMapping("/api/posts/{postId}/scrap")
    public ApiResponse<ScrapResponse> scrap(@AuthenticationPrincipal AuthUser loginUser, @PathVariable Long postId) {
        return ApiResponse.ok(scrapService.scrap(loginUser.id(), postId));
    }

    @DeleteMapping("/api/posts/{postId}/scrap")
    public ApiResponse<ScrapResponse> unscrap(@AuthenticationPrincipal AuthUser loginUser, @PathVariable Long postId) {
        return ApiResponse.ok(scrapService.unscrap(loginUser.id(), postId));
    }

    @GetMapping("/api/users/me/scraps")
    public ApiResponse<CursorPage<MyScrapResponse>> getMyScraps(@AuthenticationPrincipal AuthUser loginUser,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(scrapService.getMyScraps(loginUser.id(), cursor, size));
    }
}
