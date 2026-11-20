package com.grytonems.backend.domain.user.dto;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.user.UserStatus;

/** 정지·해제 결과. endsAt이 null이면 영구 정지(해제 응답에서는 항상 null) */
public record SuspensionResponse(Long userId, UserStatus status, LocalDateTime endsAt) {
}
