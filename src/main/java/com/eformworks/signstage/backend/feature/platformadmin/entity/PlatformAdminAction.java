package com.eformworks.signstage.backend.feature.platformadmin.entity;

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
    UPDATE_ORGANIZATION_STATUS,
    CREATE_ORGANIZATION,
    FORCE_ADD_MEMBER,
    FORCE_UPDATE_MEMBER_ROLE,
    FORCE_REMOVE_MEMBER,
    FORCE_WITHDRAW_USER,
    UPDATE_ACCOUNT_ROLE,
    REJECT_ORGANIZATION_REQUEST,
    CREATE_BILLING_PLAN,
    UPDATE_BILLING_PLAN,
    CREATE_OPTIONAL_FEATURE,
    UPDATE_OPTIONAL_FEATURE,
    CREATE_CAPACITY_ADDON,
    UPDATE_CAPACITY_ADDON,
    UPDATE_CEREMONY_STATUS
}
