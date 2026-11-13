package com.grytonems.backend.domain.user.dto;

/**
 * GET /api/users/me/withdrawal-check (AUTH-09): 탈퇴 전 경고용
 * openPostCount: 모집 중인 내 글 수, acceptedTeamCount: 수락돼 참여 중인 팀 수
 */
public record WithdrawalCheckResponse(long openPostCount, long acceptedTeamCount) {
}
