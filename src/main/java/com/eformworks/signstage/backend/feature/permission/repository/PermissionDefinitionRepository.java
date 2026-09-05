package com.eformworks.signstage.backend.feature.permission.repository;

import com.eformworks.signstage.backend.feature.permission.entity.PermissionDefinition;
import com.eformworks.signstage.backend.feature.permission.entity.RoleAxis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionDefinitionRepository extends JpaRepository<PermissionDefinition, Long> {

    List<PermissionDefinition> findAllByRoleAxisAndActiveTrueOrderByDisplayOrderAsc(RoleAxis roleAxis);

    Optional<PermissionDefinition> findByPermissionKey(String permissionKey);

    boolean existsByPermissionKey(String permissionKey);
}
