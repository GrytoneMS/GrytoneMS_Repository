package com.grytonems.backend.domain.verification;

import java.time.LocalDate;

/**
 * 인증 학기 표기 (school_verifications.semester, 예: 2026-2).
 * 학기 재인증 기준일(AUTH-08)에 맞춰 3/1~8/31은 1학기, 9/1~다음 해 2월 말은 2학기로 봅니다.
 */
public final class Semester {

    private Semester() {
    }

    public static String of(LocalDate date) {
        int month = date.getMonthValue();
        if (month >= 3 && month <= 8) {
            return date.getYear() + "-1";
        }
        int year = month <= 2 ? date.getYear() - 1 : date.getYear();
        return year + "-2";
    }
}
