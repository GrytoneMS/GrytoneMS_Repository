package com.grytonems.backend.domain.post;

/** posts.location_code 허용값 (DB 설계서 8장) */
public enum LocationCode {
    CHEONAN_TERMINAL,
    CHEONAN_ASAN_STN,
    DUJEONG_STN,
    ONYANG_STN,
    CAMPUS,
    CUSTOM
} //(천안터미널, 천안아산역, 두정역, 온양온천역, 각 캠퍼스, 직접입력 순)
