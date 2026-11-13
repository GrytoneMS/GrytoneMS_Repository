package com.grytonems.backend.domain.notification;

/** notifications.type (DB 설계서 8장) */
public enum NotificationType {
    NEW_APPLICATION,       // 팀장: 새 지원 도착
    APPLICATION_REMINDER,  // 팀장: 대기 지원 3일 미처리
    DEADLINE_SOON,         // 팀장: 모집 마감 D-1
    MEMBER_LEFT,           // 팀장: 팀원이 팀 나가기
    ACCEPTED,              // 지원자: 수락됨
    REJECTED,              // 지원자: 거절됨
    POST_CLOSED,           // 지원자: 지원한 글 마감
    CONTACT_CHANGED,       // 지원자: 연락 수단 변경
    SCRAP_DEADLINE_SOON,   // 스크랩한 사용자: 마감 D-1
    REVERIFY_REQUIRED,     // 전체: 학기 재인증 요청
    REPORT_RESOLVED,       // 신고자: 신고 처리 완료
    VERIFY_RESULT,         // 서류 인증 결과
    POST_HIDDEN,           // 팀장: 내 모집글 숨김 (1차 추가)
    POST_RESTORED,         // 팀장: 내 모집글 복구 (1차 추가)
    POST_DELETED,          // 팀장: 관리자가 신고 처리로 내 모집글 삭제 (1차 추가)
    MEMBER_REMOVED         // 팀원: 팀장이 팀에서 내보냄 (1차 추가)
}
