package com.grytonems.backend.global.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 로그인 토큰(JWT) 발급·검증.
 * 토큰에는 사용자 id만 담고, 상태·권한은 요청마다 DB에서 읽습니다
 * (학교 인증·정지 같은 상태 변경이 바로 반영되도록).
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + properties.accessTokenExpiration().toMillis());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(key)
                .compact();
    }

    /** 유효한 토큰이면 사용자 id, 위조·만료·형식 오류면 빈 값 */
    public Optional<Long> parseUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Long.parseLong(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
