package com.grytonems.backend.domain.post.dto;

/** POST-01 응답: 만든 글 id (프론트는 이 id로 상세 화면으로 이동) */
public record PostCreateResponse(Long id) {
}