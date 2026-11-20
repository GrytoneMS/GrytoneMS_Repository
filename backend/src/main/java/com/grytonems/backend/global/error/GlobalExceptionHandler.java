package com.grytonems.backend.global.error;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * 모든 컨트롤러의 예외를 한곳에서 ErrorResponse 형식으로 바꿉니다.
 * 각 기능에서는 try-catch 없이 BusinessException 을 던지기만 하면 됩니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 비즈니스 규칙 위반 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, e.getMessage()));
    }

    /** @Valid 검증 실패: 어느 필드가 왜 틀렸는지 함께 내려줍니다 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT, fieldErrors));
    }

    /** JSON 형식 오류, 잘못된 enum 값 등 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT));
    }

    /** 업로드 파일이 너무 크거나 multipart 형식이 아님 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_FILE, "5MB 이하 파일만 올릴 수 있어요"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_FILE, "파일을 선택해 주세요"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_FILE, "파일을 선택해 주세요"));
    }

    /** 없는 URL (예: 브라우저가 자동 요청하는 /favicon.ico) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(ErrorCode.NOT_FOUND));
    }

    /** URL은 맞는데 HTTP 메서드가 다를 때 (예: POST만 되는 곳에 GET) */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /** 주소·쿼리 값의 형식 오류 (예: /api/posts/abc, ?status=없는값) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT));
    }

    /** 본문 형식이 JSON이 아닐 때 (예: Content-Type 없이 보낸 POST). 예전에는 500으로 떨어졌습니다 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT,
                "요청 형식을 확인해 주세요 (Content-Type: application/json)"));
    }

    /** 필수 쿼리·헤더가 빠졌을 때. 예전에는 500으로 떨어졌습니다 */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ErrorResponse> handleBinding(ServletRequestBindingException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_INPUT));
    }

    /** 예상하지 못한 오류 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
