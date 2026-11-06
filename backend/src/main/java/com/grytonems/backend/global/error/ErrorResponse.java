package com.grytonems.backend.global.error;

import java.util.List;

/**
 * 실패 응답 공통 형식.
 *
 * <pre>
 * {
 *   "success": false,
 *   "code": "INVALID_INPUT",
 *   "message": "입력값을 확인해 주세요",
 *   "fieldErrors": [ { "field": "title", "message": "5~40자로 입력해 주세요" } ]
 * }
 * </pre>
 *
 * fieldErrors 는 입력 검증 실패일 때만 채워집니다.
 * 프론트는 field 값으로 해당 입력칸으로 이동합니다 (POST-01).
 */
public record ErrorResponse(boolean success, String code, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(false, errorCode.name(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(false, errorCode.name(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(false, errorCode.name(), errorCode.getMessage(), fieldErrors);
    }
}
