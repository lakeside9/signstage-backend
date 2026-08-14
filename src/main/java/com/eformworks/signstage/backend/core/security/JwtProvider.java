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

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_PLATFORM_ROLE = "platformRole";
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
