package com.grytonems.backend.domain.report;

/**
 * reports.action: 관리자 조치 (ADMN-02)
 * 처리하는 조치: NONE(문제없음), 모집글 HIDE(숨김)·DELETE(삭제), SUSPEND(이용 정지).
 * SUSPEND(이용 정지)는 대상의 작성자를 정지합니다 (ADMN-04). DELETE(삭제)는 모집글만, 지원자가 있어도 삭제합니다.
 */
public enum ReportAction {
    NONE,
    HIDE,
    DELETE,
    SUSPEND
}
