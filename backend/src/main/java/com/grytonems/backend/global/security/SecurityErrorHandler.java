package com.grytonems.backend.global.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.global.error.ErrorCode;
import com.grytonems.backend.global.error.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그인·권한 실패를 공통 실패 응답 형식으로 내려줍니다.
 * - 로그인 안 함·토큰 만료 → 401 UNAUTHORIZED (로그인 화면으로)
 * - 정지 회원 → 403 SUSPENDED
 * - 학교 인증이 필요한 기능 → 403 UNVERIFIED (학교 인증 화면으로)
 * - 관리자 전용 등 → 403 FORBIDDEN
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(response, resolveDeniedCode(request));
    }

    private ErrorCode resolveDeniedCode(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            return ErrorCode.FORBIDDEN;
        }
        if (user.status() == UserStatus.SUSPENDED) {
            return ErrorCode.SUSPENDED;
        }
        boolean adminPath = request.getRequestURI().startsWith("/api/admin");
        if (!adminPath && user.status() != UserStatus.VERIFIED) {
            return ErrorCode.UNVERIFIED;
        }
        return ErrorCode.FORBIDDEN;
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code));
    }
}
