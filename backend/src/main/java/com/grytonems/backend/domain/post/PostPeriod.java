package com.grytonems.backend.domain.post;

/**
 * LIST-03 활동 기간 필터 (활동 종료일 - 시작일, 일 단위)
 */
public enum PostPeriod {
    WEEK(0, 7),                    // 1주 이내
    MONTH(0, 31),                  // 1개월 이내
    THREE_MONTHS(0, 92),           // 3개월 이내
    OVER_THREE_MONTHS(93, 100000); // 3개월 이상

    private final int minDays;
    private final int maxDays;

    PostPeriod(int minDays, int maxDays) {
        this.minDays = minDays;
        this.maxDays = maxDays;
    }

    public int minDays() {
        return minDays;
    }

    public int maxDays() {
        return maxDays;
    }
}