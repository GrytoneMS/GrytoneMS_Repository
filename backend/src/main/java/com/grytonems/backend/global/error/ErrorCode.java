package com.grytonems.backend.global.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서비스 전체 오류 코드 목록.
 * 새 기능에서 오류가 필요하면 여기에 한 줄 추가하고 BusinessException 으로 던집니다.
 * 프론트는 code 값으로 분기합니다 (예: UNAUTHORIZED → 로그인 화면, UNVERIFIED → 학교 인증 화면).
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없어요"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요"),

    // 권한 (기능 명세서 0장 사용자 권한)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요"),
    UNVERIFIED(HttpStatus.FORBIDDEN, "학교 인증이 필요해요"),
    SUSPENDED(HttpStatus.FORBIDDEN, "이용이 정지된 계정이에요"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요"),

    // 회원·로그인 (AUTH-01, AUTH-04)
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요"),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임이에요"),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않아요"),
    LOGIN_LOCKED(HttpStatus.LOCKED, "로그인에 5회 실패해 잠시 잠겼어요"),

    // 학교 이메일 인증 (AUTH-06)
    SCHOOL_EMAIL_DOMAIN_MISMATCH(HttpStatus.BAD_REQUEST, "선택한 학교의 이메일 주소가 아니에요"),
    SCHOOL_EMAIL_IN_USE(HttpStatus.CONFLICT, "다른 계정에서 이미 인증한 학교 이메일이에요"),
    VERIFICATION_SEND_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "인증 코드는 하루 5회까지 받을 수 있어요"),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료됐어요. 코드를 다시 받아 주세요"),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증 코드가 맞지 않아요"),
    EMAIL_SEND_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요"),

    // 지원 (APPL)
    CANNOT_APPLY_OWN_POST(HttpStatus.BAD_REQUEST, "내가 올린 모집글에는 지원할 수 없어요"),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 지원한 모집글이에요"),
    ROLE_CLOSED(HttpStatus.CONFLICT, "모집이 마감된 역할이에요"),
    INVALID_APPLICATION_STATUS(HttpStatus.CONFLICT, "이미 처리된 지원이에요"),
    ROLE_FULL(HttpStatus.CONFLICT, "이 역할은 모집 인원이 다 찼어요"),

    // 신고 (SAFE-01, ADMN-02)
    CANNOT_REPORT_SELF(HttpStatus.BAD_REQUEST, "내 글이나 나 자신은 신고할 수 없어요"),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 대상이에요"),
    REPORT_ALREADY_RESOLVED(HttpStatus.CONFLICT, "이미 처리된 신고예요"),
    REPORT_ACTION_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "이 대상에는 할 수 없는 조치예요"),

    // 이용 정지 (ADMN-04)
    ALREADY_SUSPENDED(HttpStatus.CONFLICT, "이미 이용 정지된 사용자예요"),
    NOT_SUSPENDED(HttpStatus.CONFLICT, "이용 정지된 사용자가 아니에요"),

    // 차단 (SAFE-03)
    CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "나 자신은 차단할 수 없어요"),
    ALREADY_BLOCKED(HttpStatus.CONFLICT, "이미 차단한 사용자예요"),
    BLOCKED_USER(HttpStatus.FORBIDDEN, "차단한 사용자예요"),

    // 학교 (예시 기능)
    SCHOOL_NOT_FOUND(HttpStatus.NOT_FOUND, "학교를 찾을 수 없어요"),
    SCHOOL_DOMAIN_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 이메일 도메인이에요"),

    // 모집글
    POST_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "모집 중인 글은 최대 3개까지 올릴 수 있어요"),
    POST_ALREADY_CLOSED(HttpStatus.CONFLICT, "이미 마감된 모집글이에요"),
    POST_ALREADY_HIDDEN(HttpStatus.CONFLICT, "이미 숨김 처리된 모집글이에요"),
    POST_NOT_HIDDEN(HttpStatus.CONFLICT, "숨김 처리된 모집글이 아니에요"),

    // 스크랩 (SCRP)
    CANNOT_SCRAP_OWN_POST(HttpStatus.BAD_REQUEST, "내가 올린 모집글은 스크랩할 수 없어요");

    private final HttpStatus status;
    private final String message;
}
