package com.grytonems.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "unit-test-secret-0123456789-0123456789-xyz";

    @Test
    @DisplayName("발급한 토큰에서 사용자 id를 꺼낼 수 있다")
    void createAndParse() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));

        String token = provider.createAccessToken(42L);

        assertThat(provider.parseUserId(token)).contains(42L);
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void expiredToken() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofSeconds(-60)));

        String token = provider.createAccessToken(42L);

        assertThat(provider.parseUserId(token)).isEmpty();
    }

    @Test
    @DisplayName("다른 키로 서명했거나 형식이 틀린 토큰은 거부한다")
    void forgedOrMalformedToken() {
        JwtProvider provider = new JwtProvider(new JwtProperties(SECRET, Duration.ofHours(1)));
        JwtProvider attacker = new JwtProvider(
                new JwtProperties("attacker-secret-0123456789-0123456789-xyz", Duration.ofHours(1)));

        assertThat(provider.parseUserId(attacker.createAccessToken(42L))).isEmpty();
        assertThat(provider.parseUserId("abc.def.ghi")).isEmpty();
        assertThat(provider.parseUserId("")).isEmpty();
    }

    @Test
    @DisplayName("서명 키가 32바이트보다 짧으면 서버가 뜨지 않는다")
    void shortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
