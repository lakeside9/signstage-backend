package com.eformworks.signstage.backend.feature.permission.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * {@link RolePermission} 변경 이력 — append-only. **결정(2026-09-04)**: 값이 바뀔 때마다 변경
 * 후 스냅샷을 새 행으로 INSERT하고 기존 행은 UPDATE/DELETE하지 않는다. 권한 변경과 이력 적재는
 * 반드시 같은 트랜잭션에서 처리한다 — signstage-docs
 * business/menu-and-action-permission-management-review.md 7.4절.
 */
@Entity
@Table(name = "role_permission_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class RolePermissionHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_definition_id", nullable = false)
    private PermissionDefinition permissionDefinition;

    @Column(name = "role_value", nullable = false, length = 30)
    private String roleValue;

    @Column(nullable = false)
    private boolean allowed;

    @Builder
    private RolePermissionHistory(RolePermission rolePermission) {
        this.permissionDefinition = rolePermission.getPermissionDefinition();
        this.roleValue = rolePermission.getRoleValue();
        this.allowed = rolePermission.isAllowed();
    }
}
