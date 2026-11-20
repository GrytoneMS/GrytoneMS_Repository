package com.grytonems.backend.domain.application;

/** applications.reject_reason (DB 설계서 8장) */
public enum RejectReason {
    ROLE_MISMATCH, // 역할 조건 불일치
    SCHEDULE,      // 일정 불일치
    FILLED,        // 다른 지원자 선정
    ETC            // 기타
}
