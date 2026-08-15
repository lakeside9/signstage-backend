package com.eformworks.signstage.backend.core.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer 헤더의 JWT를 검증해 SecurityContext에 {@link CurrentUser}를 채운다.
 * 토큰이 없거나 검증에 실패하면 조용히 넘어간다 — 이후 SecurityConfig의
 * {@code authorizeHttpRequests}/{@code exceptionHandling}이 인증 필요 여부를 판단한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtProvider.parseAccessTokenClaims(token);

                Long userId = claims.get(JwtProvider.CLAIM_USER_ID, Long.class);
                String loginId = claims.getSubject();
                String platformRole = claims.get(JwtProvider.CLAIM_PLATFORM_ROLE, String.class);

                CurrentUser currentUser = new CurrentUser(userId, loginId, platformRole);
                List<GrantedAuthority> authorities = platformRole != null
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + platformRole))
                        : List.of();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(currentUser, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // 토큰 검증 실패 시 SecurityContext를 비워둔다(익명 사용자로 처리됨).
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
