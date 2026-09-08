package com.eformworks.signstage.backend.feature.permission.repository;

import com.eformworks.signstage.backend.feature.permission.entity.PermissionDefinition;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import com.eformworks.signstage.backend.feature.permission.entity.RolePermission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    /** {@code RolePermissionService}의 역할별 캐시가 쓰는 조회 — 인덱스(role_value) 하나로 끝난다. */
    List<RolePermission> findAllByRoleValueAndAllowedTrueAndPermissionDefinitionActiveTrue(String roleValue);

    List<RolePermission> findAllByPermissionDefinitionRoleAxis(RoleAxis roleAxis);

    Optional<RolePermission> findByPermissionDefinitionAndRoleValue(PermissionDefinition permissionDefinition, String roleValue);

    boolean existsByPermissionDefinition(PermissionDefinition permissionDefinition);
}
