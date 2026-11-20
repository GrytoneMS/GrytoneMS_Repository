package com.grytonems.backend.global.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 jwt.* 설정 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTokenExpiration) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret(JWT_SECRET)은 32바이트 이상이어야 합니다");
        }
        if (accessTokenExpiration == null) {
            accessTokenExpiration = Duration.ofHours(24);
        }
    }
}
