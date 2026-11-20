package com.grytonems.backend.domain.post;

import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

/** LIST-02 목록 정렬 */
public enum PostSort {
    LATEST,    // 최신순 (기본)
    DEADLINE;  // 마감 임박순

    /** 쿼리 값 → 정렬. 없으면 최신순, 잘못된 값이면 400 */
    public static PostSort from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        try {
            return PostSort.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "정렬은 LATEST 또는 DEADLINE만 가능해요");
        }
    }
}