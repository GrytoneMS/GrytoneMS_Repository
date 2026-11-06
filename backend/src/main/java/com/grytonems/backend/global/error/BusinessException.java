package com.grytonems.backend.global.error;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 시 던지는 예외.
 * 예: throw new BusinessException(ErrorCode.SCHOOL_NOT_FOUND);
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
