package com.grytonems.backend.domain.report;

/** reports.reason_code (DB 설계서 8장 신고 사유) */
public enum ReportReason {
    ABUSE,    // 욕설·비하
    FAKE,     // 허위 모집
    SPAM,     // 홍보·스팸
    PRIVACY,  // 개인정보 노출
    ETC       // 기타
}
