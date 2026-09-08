package com.eformworks.signstage.backend.feature.permission.service;

import com.eformworks.signstage.backend.feature.identity.entity.PlatformRole;
import com.eformworks.signstage.backend.feature.organization.entity.MemberRole;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import java.util.Arrays;
import java.util.List;

/**
 * {@link RoleAxis}에 속한 실제 역할값 목록. {@code role_permissions}를 "촘촘한 표"로 유지하려면
 * (7.3절) 새 {@link com.eformworks.signstage.backend.feature.permission.entity.PermissionDefinition}이
 * 생길 때마다 이 목록 전체에 기본 행을 만들어야 한다.
 */
final class RoleAxisRoles {

    private RoleAxisRoles() {
    }

    static List<String> of(RoleAxis roleAxis) {
        return switch (roleAxis) {
            case PLATFORM -> Arrays.stream(PlatformRole.values()).map(Enum::name).toList();
            case ORGANIZATION -> Arrays.stream(MemberRole.values()).map(Enum::name).toList();
        };
    }
}
