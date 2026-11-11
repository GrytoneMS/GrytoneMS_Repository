package com.grytonems.backend.domain.verification;

/** 발송했지만 아직 확인하지 않은 인증 코드 (5분간 보관) */
public record PendingVerification(Long schoolId, String schoolEmail, String code, int failCount) {
}
