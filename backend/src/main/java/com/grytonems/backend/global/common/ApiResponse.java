package com.grytonems.backend.global.common;

/**
 * 성공 응답 공통 형식.
 *
 * <pre>
 * { "success": true, "data": { ... } }
 * </pre>
 *
 * 실패 응답은 global.error.ErrorResponse 를 씁니다.
 */
public record ApiResponse<T>(boolean success, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null);
    }
}
