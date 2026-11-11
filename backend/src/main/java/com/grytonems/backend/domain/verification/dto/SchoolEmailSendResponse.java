package com.grytonems.backend.domain.verification.dto;

/**
 * 코드 발송 결과.
 * expiresInSeconds: 코드 유효 시간(초), 화면 타이머용
 * remainingSendCount: 오늘 더 받을 수 있는 횟수
 */
public record SchoolEmailSendResponse(String schoolEmail, int expiresInSeconds, int remainingSendCount) {
}
