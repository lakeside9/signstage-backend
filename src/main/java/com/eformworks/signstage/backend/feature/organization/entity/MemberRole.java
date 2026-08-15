package com.eformworks.signstage.backend.feature.organization.entity;

/**
 * 조직 내 권한 4단계. 플랫폼 전역 권한({@code User.platformRole})과는 다른 축이다
 * (signstage-docs business/user-organization-design.md 4.1/9장 참고).
 */
public enum MemberRole {
    OWNER,
    ADMIN,
    OPERATOR,
    VIEWER
}
