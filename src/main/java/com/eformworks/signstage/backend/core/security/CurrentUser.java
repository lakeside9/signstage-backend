package com.eformworks.signstage.backend.core.security;

/**
 * JWT access token에서 뽑아낸 인증 주체. {@code JwtAuthenticationFilter}가
 * {@code SecurityContext}의 principal로 설정하며, 컨트롤러는
 * {@code @AuthenticationPrincipal CurrentUser}로 바로 받을 수 있다.
 *
 * <p>{@code platformRole}은 플랫폼 관리자 토큰에서만 채워진다. 일반 사용자 토큰은
 * {@code platformRole}이 항상 {@code null}이며, {@code organizationId}/{@code role} 클레임은
 * 아직 싣지 않는다(organization_members 기반 조직 선택 흐름 — signstage-docs
 * business/user-organization-design.md 5.2절 참고). 조직 API는 그 대신 매 요청마다
 * {@code userId}로 organization_members를 직접 조회해 권한을 판단한다.
 */
public record CurrentUser(Long userId, String loginId, String platformRole) {
}
