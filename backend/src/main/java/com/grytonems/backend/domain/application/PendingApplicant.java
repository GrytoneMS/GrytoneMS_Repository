package com.grytonems.backend.domain.application;

/** 대기 지원자와 그 글 제목 (ADMN-04 정지 시 마감 알림용) */
public record PendingApplicant(Long applicantId, String postTitle) {
}
