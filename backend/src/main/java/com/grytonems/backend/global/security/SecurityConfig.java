package com.grytonems.backend.global.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.global.config.CorsProperties;

/**
 * 주소별 접근 권한 (기능 명세서 0장 사용자 권한).
 *
 * 새 API를 만들 때:
 * - 비회원도 봐야 하면 아래 permitAll 목록에 추가
 * - 학교 인증 회원만 쓸 수 있으면 VERIFIED 목록에 추가
 * - 둘 다 아니면 따로 할 일 없음 (/api/** 는 기본적으로 로그인 필요)
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProvider jwtProvider,
            UserRepository userRepository, SecurityErrorHandler errorHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 브라우저 CORS 사전 요청
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 비회원 가능
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/schools").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*").permitAll()

                        // 관리자만
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 학교 인증 회원만 (글쓰기 POST-01, 지원 APPL-01, 스크랩 SCRP-01, 신고 SAFE-01)
                        // 신고는 가입만 한 계정 여러 개로 남의 글을 숨기지 못하게 인증 회원만 받습니다
                        .requestMatchers(HttpMethod.POST, "/api/reports").hasAuthority(AuthUser.VERIFIED_AUTHORITY)
                        .requestMatchers(HttpMethod.POST, "/api/posts", "/api/posts/*/applications")
                                .hasAuthority(AuthUser.VERIFIED_AUTHORITY)
                        .requestMatchers(HttpMethod.PUT, "/api/posts/*/scrap").hasAuthority(AuthUser.VERIFIED_AUTHORITY)

                        // 그 밖의 API는 로그인 필요
                        .requestMatchers("/api/**").authenticated()

                        // /api 밖의 주소(없는 주소 포함)는 그대로 통과 → 404 처리
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 프론트 개발 서버(기본 http://localhost:5173)에서 API를 호출할 수 있게 허용 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
