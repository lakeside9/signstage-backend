package com.eformworks.signstage.backend.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link JwtProvider} 발급/검증 단위 테스트. core가 feature 엔티티({@code User})를 모르도록
 * userId/loginId/platformRole 기본값만 받는 구조를 검증한다.
 */
class JwtProviderTest {

    private final JwtProvider jwtProvider = new JwtProvider(
            "test-only-jwt-secret-that-is-at-least-32-bytes-long", 60, 480, 10
    );

    @Test
    @DisplayName("플랫폼 관리자 토큰은 userId·loginId·platformRole 클레임을 싣는다")
    void createPlatformAccessToken_carriesPlatformRole() {
        String token = jwtProvider.createPlatformAccessToken(1L, "admin", "PLATFORM_OPS");

        Claims claims = jwtProvider.parseAccessTokenClaims(token);

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get(JwtProvider.CLAIM_USER_ID, Long.class)).isEqualTo(1L);
        assertThat(claims.get(JwtProvider.CLAIM_PLATFORM_ROLE, String.class)).isEqualTo("PLATFORM_OPS");
    }

    @Test
    @DisplayName("일반 사용자 토큰에는 platformRole 클레임이 없다")
    void createUserAccessToken_hasNoPlatformRole() {
        String token = jwtProvider.createUserAccessToken(2L, "user");

        Claims claims = jwtProvider.parseAccessTokenClaims(token);

        assertThat(claims.get(JwtProvider.CLAIM_USER_ID, Long.class)).isEqualTo(2L);
        assertThat(claims.get(JwtProvider.CLAIM_PLATFORM_ROLE, String.class)).isNull();
    }

    @Test
    @DisplayName("비밀번호 재설정 토큰은 userId를 돌려주고 일반 API 인증에는 쓸 수 없다")
    void passwordResetToken_roundTripAndRejectedAsAccessToken() {
        String token = jwtProvider.createPasswordResetToken(3L);

        assertThat(jwtProvider.parsePasswordResetToken(token)).isEqualTo(3L);
        assertThatThrownBy(() -> jwtProvider.parseAccessTokenClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("access token을 재설정 토큰으로 쓰면 JwtException을 던진다")
    void parsePasswordResetToken_withAccessToken_throwsJwtException() {
        String accessToken = jwtProvider.createUserAccessToken(2L, "user");

        assertThatThrownBy(() -> jwtProvider.parsePasswordResetToken(accessToken)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("서명이 맞지 않는 토큰은 JwtException을 던진다")
    void parsePasswordResetToken_withForeignToken_throwsJwtException() {
        JwtProvider other = new JwtProvider("another-test-only-jwt-secret-32-bytes-or-more!", 60, 480, 10);
        String foreignToken = other.createPasswordResetToken(3L);

        assertThatThrownBy(() -> jwtProvider.parsePasswordResetToken(foreignToken)).isInstanceOf(JwtException.class);
    }
}
