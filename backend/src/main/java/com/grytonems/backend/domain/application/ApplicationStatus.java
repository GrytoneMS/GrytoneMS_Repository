package com.grytonems.backend.domain.application;

/** applications.status (DB 설계서 8장) */
public enum ApplicationStatus {
    PENDING,   // 대기
    ACCEPTED,  // 수락
    REJECTED,  // 거절
    CANCELED,  // 지원자가 취소
    CLOSED,    // 모집 마감으로 자동 종료
    LEFT       // 수락 후 팀에서 나감
}
