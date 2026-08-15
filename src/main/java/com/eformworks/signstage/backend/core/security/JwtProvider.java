package com.eformworks.signstage.backend.core.security;

import com.eformworks.signstage.backend.core.error.ApplicationException;
import com.eformworks.signstage.backend.feature.identity.error.IdentityErrorCode;
import com.eformworks.signstage.backend.feature.identity.repository.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 발급/검증을 담당한다. 이번 최소 구현 범위(플랫폼 관리자 로그인)에서 필요한
 * 두 종류의 토큰만 다룬다 — 조직 로그인용 토큰(organizationId 클레임 포함)은
 * organization_members 등이 구현된 뒤 추가한다(signstage-docs business/user-organization-design.md 5.2/7.6절).
 */
@Component
public class JwtProvider {

    static final String CLAIM_USER_ID = "userId";
    static final String CLAIM_PLATFORM_ROLE = "platformRole";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";

    private final SecretKey secretKey;
    private final long platformTokenExpiryMinutes;
    private final long passwordResetTokenExpiryMinutes;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.platform-token-expiry-minutes:60}") long platformTokenExpiryMinutes,
            @Value("${jwt.password-reset-token-expiry-minutes:10}") long passwordResetTokenExpiryMinutes
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.platformTokenExpiryMinutes = platformTokenExpiryMinutes;
        this.passwordResetTokenExpiryMinutes = passwordResetTokenExpiryMinutes;
    }

    /**
     * 플랫폼 관리자 콘솔 토큰. organizationId 클레임이 없다(signstage-docs 7.6절).
     */
    public String createPlatformAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getLoginId())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_PLATFORM_ROLE, user.getPlatformRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(platformTokenExpiryMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 강제 비밀번호 변경 전용 토큰. 이 토큰으로는 force-password-change API만 호출할 수 있다
     * (signstage-docs business/login-security.md 5.3절).
     */
    public String createPasswordResetToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_PURPOSE, PURPOSE_PASSWORD_RESET)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(passwordResetTokenExpiryMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
    }

    public Long parsePasswordResetToken(String token) {
        Claims claims = parseClaims(token);

        if (!PURPOSE_PASSWORD_RESET.equals(claims.get(CLAIM_PURPOSE, String.class))) {
            throw new ApplicationException(IdentityErrorCode.INVALID_RESET_TOKEN);
        }

        return claims.get(CLAIM_USER_ID, Long.class);
    }

    /**
     * 일반 API 인증(Authorization: Bearer)에 쓰는 access token을 검증한다.
     * {@code core.security.JwtAuthenticationFilter}에서만 호출한다.
     *
     * <p>MVC 계층 밖(서블릿 필터)에서 실행되므로 {@code GlobalExceptionHandler}가 개입할 수 없다 —
     * 그래서 {@code ApplicationException}으로 감싸지 않고 jjwt의 원본 예외를 그대로 던진다.
     * 필터가 이를 잡아 인증 실패로 처리하면, Spring Security의 인증 실패 처리 흐름을 탄다.
     *
     * <p>비밀번호 재설정 토큰처럼 특수 목적 토큰은 일반 API 인증에 쓸 수 없도록 거부한다.
     */
    public Claims parseAccessTokenClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (claims.get(CLAIM_PURPOSE, String.class) != null) {
            throw new JwtException("특수 목적 토큰은 일반 API 인증에 사용할 수 없습니다.");
        }

        return claims;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApplicationException(IdentityErrorCode.INVALID_RESET_TOKEN, e);
        }
    }
}
