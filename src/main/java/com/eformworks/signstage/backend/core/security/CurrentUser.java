package com.eformworks.signstage.backend.core.security;

/**
 * JWT access token에서 뽑아낸 인증 주체. {@code JwtAuthenticationFilter}가
 * {@code SecurityContext}의 principal로 설정하며, 컨트롤러는
 * {@code @AuthenticationPrincipal CurrentUser}로 바로 받을 수 있다.
 *
 * <p>{@code platformRole}은 플랫폼 관리자 토큰에서만 채워진다(organization_members
 * 기반 조직 토큰은 아직 없다 — signstage-docs business/user-organization-design.md 5.2절 참고).
 */
public record CurrentUser(Long userId, String loginId, String platformRole) {
}
