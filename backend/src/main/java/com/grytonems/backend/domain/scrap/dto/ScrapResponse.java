package com.grytonems.backend.domain.scrap.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** PUT·DELETE /api/posts/{postId}/scrap: 처리 후 스크랩 상태 */
public record ScrapResponse(Long postId, @JsonProperty("isScrapped") boolean isScrapped) {
}
