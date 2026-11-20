package com.grytonems.backend.domain.application;

/** 역할별·상태별 지원 수 (APPL-11 내 모집글의 지원자 수 표시용) */
public record RoleApplicationCount(Long postRoleId, ApplicationStatus status, long count) {
}
