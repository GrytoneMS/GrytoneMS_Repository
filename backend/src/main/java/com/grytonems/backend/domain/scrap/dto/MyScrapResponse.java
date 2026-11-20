package com.grytonems.backend.domain.scrap.dto;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.post.dto.PostCardResponse;

/**
 * GET /api/users/me/scraps (SCRP-02) 항목 하나.
 * post는 목록 카드(GET /api/posts)와 같은 모양이라 같은 카드 컴포넌트로 그리면 됩니다. post.status로 모집중·마감 표시.
 */
public record MyScrapResponse(Long scrapId, LocalDateTime scrappedAt, PostCardResponse post) {
}
