package com.grytonems.backend.global.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authorization: Bearer {토큰} 헤더를 읽어 로그인 사용자를 등록합니다.
 * 토큰이 없거나 잘못돼도 여기서는 막지 않고 비회원으로 통과시킵니다.
 * 로그인이 필요한 주소인지는 SecurityConfig가 판단합니다.
 *
 * SecurityConfig에서 직접 생성합니다 (@Component로 만들면 필터가 두 번 등록됩니다).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            jwtProvider.parseUserId(token)
                    .flatMap(userRepository::findById)
                    .filter(user -> user.getStatus() != UserStatus.WITHDRAWN)
                    .map(AuthUser::from)
                    .ifPresent(authUser -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(authUser, null, authUser.authorities())));
        }
        chain.doFilter(request, response);
    }
}
