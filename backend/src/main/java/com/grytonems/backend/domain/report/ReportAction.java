package com.grytonems.backend.domain.report;

/**
 * reports.action: 관리자 조치 (ADMN-02)
 * 1차에서 실제로 처리하는 조치는 NONE(문제없음)과 모집글 HIDE(숨김)입니다.
 * DELETE(삭제)는 모집글 삭제(POST-03), SUSPEND(이용 정지)는 ADMN-04가 생기면 연결합니다.
 */
public enum ReportAction {
    NONE,
    HIDE,
    DELETE,
    SUSPEND
}
