package com.eformworks.signstage.backend.feature.identity.entity;

/**
 * 조직에 속하지 않는 전역 플랫폼 권한. 일반 사용자는 이 값이 없다(User.platformRole == null).
 */
public enum PlatformRole {
    PLATFORM_SUPPORT,
    PLATFORM_OPS,
    PLATFORM_SUPER
}
