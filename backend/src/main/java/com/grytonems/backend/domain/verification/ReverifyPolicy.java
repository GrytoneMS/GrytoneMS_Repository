package com.grytonems.backend.domain.verification;

import java.time.LocalDate;

import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserStatus;

/**
 * 학기 재인증 (AUTH-08).
 * 3월 1일·9월 1일에 학기가 바뀌고, 그 전에 인증한 회원은 2주 안(시작일 포함 14일, 3/14·9/14까지)에 다시 인증해야 합니다.
 * 기한이 지나면 매일 자정 배치가 미인증으로 바꿉니다.
 */
public final class ReverifyPolicy {

    public static final int GRACE_DAYS = 14;

    private ReverifyPolicy() {
    }

    /** today가 속한 학기의 시작일 (3/1 또는 9/1) */
    public static LocalDate semesterStart(LocalDate today) {
        int month = today.getMonthValue();
        if (month >= 3 && month <= 8) {
            return LocalDate.of(today.getYear(), 3, 1);
        }
        int year = month <= 2 ? today.getYear() - 1 : today.getYear();
        return LocalDate.of(year, 9, 1);
    }

    /** 재인증 마지막 날 */
    public static LocalDate dueDate(LocalDate semesterStart) {
        return semesterStart.plusDays(GRACE_DAYS - 1);
    }

    /** 이 회원이 지금 재인증해야 하면 마지막 날, 아니면 null (GET /api/users/me의 reverifyDueDate) */
    public static LocalDate dueDateOf(User user, LocalDate today) {
        if (user.getStatus() != UserStatus.VERIFIED || user.getVerifiedAt() == null) {
            return null;
        }
        LocalDate start = semesterStart(today);
        LocalDate due = dueDate(start);
        if (today.isAfter(due) || !user.getVerifiedAt().toLocalDate().isBefore(start)) {
            return null;
        }
        return due;
    }
}
