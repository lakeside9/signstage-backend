package com.eformworks.signstage.backend.feature.permission.entity;

/**
 * 메뉴/권한이 속한 역할 축. 플랫폼 관리자({@code PlatformRole})와 조직 사용자({@code MemberRole})는
 * 겸직이 불가능한 상호 배타적 축이라({@code User.platformRole}과 조직 멤버십이 동시에 존재할 수
 * 없다) 메뉴 트리·권한키도 완전히 분리한다 — signstage-docs
 * business/menu-and-action-permission-management-review.md 5장. {@link Menu#getConsole()}과
 * {@link PermissionDefinition#getRoleAxis()}가 같은 값 집합을 공유한다.
 */
public enum RoleAxis {
    PLATFORM,
    ORGANIZATION
}
