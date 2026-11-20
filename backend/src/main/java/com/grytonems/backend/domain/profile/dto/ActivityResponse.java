package com.grytonems.backend.domain.profile.dto;

import java.time.LocalDate;

/** 서비스 활동 이력 (PROF-08): 수락된 팀. left가 true면 "중도 하차" */
public record ActivityResponse(
        Long postId,
        String postTitle,
        String roleName,
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean left
) {
}
