package com.grytonems.backend.domain.report;

/**
 * reports.action: 관리자 조치 (ADMN-02)
 * 처리하는 조치: NONE(문제없음), 모집글 HIDE(숨김), SUSPEND(이용 정지).
 * SUSPEND(이용 정지)는 대상의 작성자를 정지합니다 (ADMN-04). DELETE(삭제)는 모집글 삭제(POST-03)가 생기면 연결합니다.
 */
public enum ReportAction {
    NONE,
    HIDE,
    DELETE,
    SUSPEND
}
