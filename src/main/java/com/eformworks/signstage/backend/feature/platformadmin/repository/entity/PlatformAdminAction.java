package com.eformworks.signstage.backend.feature.platformadmin.repository.entity;

/**
 * 플랫폼 관리자가 조직 스코핑을 우회해 수행하는 제어 행위의 종류
 * (signstage-docs business/user-organization-design.md 7.4절).
 */
public enum PlatformAdminAction {
    UPDATE_USER_STATUS,
    UNLOCK_USER,
    FORCE_PASSWORD_RESET,
    CREATE_USER,
    CREATE_ACCOUNT,
    REVOKE_ACCOUNT,
    UPDATE_ORGANIZATION_STATUS
}
