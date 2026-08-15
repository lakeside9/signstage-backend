package com.eformworks.signstage.backend.feature.identity.repository.entity;

/**
 * PENDING은 회원가입 직후 승인 대기 상태다. 이 상태에서는 로그인할 수 없다
 * (signstage-docs business/user-organization-design.md 5.1절 (a)).
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    DISABLED,
    WITHDRAWN
}
