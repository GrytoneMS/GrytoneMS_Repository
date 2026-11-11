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

    // 학교 (예시 기능)
    SCHOOL_NOT_FOUND(HttpStatus.NOT_FOUND, "학교를 찾을 수 없어요"),
    SCHOOL_DOMAIN_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 이메일 도메인이에요");

    private final HttpStatus status;
    private final String message;
}
