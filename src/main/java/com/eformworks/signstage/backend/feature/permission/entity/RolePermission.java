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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 역할별 허용 여부(설정값) — 관리 화면이 실제로 편집하는 대상. signstage-docs
 * business/menu-and-action-permission-management-review.md 7.3절.
 *
 * <p>일부러 성긴(sparse) 대신 촘촘한(dense) 표로 유지한다 — 새 {@link PermissionDefinition}이
 * 생기면 그 축에 속한 모든 역할값에 대해 즉시 기본값으로 이 행을 함께 만든다(서비스 레이어
 * 책임). 관리 화면은 "없는 조합"을 걱정할 필요 없이 항상 존재하는 행의 {@code allowed}만
 * 뒤집으면 된다.
 */
@Entity
@Table(name = "role_permissions", uniqueConstraints = @UniqueConstraint(columnNames = {"permission_definition_id", "role_value"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_definition_id", nullable = false)
    private PermissionDefinition permissionDefinition;

    /** 'PLATFORM_OPS' 또는 'OWNER' 등 — role_axis에 대응하는 PlatformRole/MemberRole 값. */
    @Column(name = "role_value", nullable = false, length = 30)
    private String roleValue;

    @Column(nullable = false)
    private boolean allowed;

    @Builder
    private RolePermission(PermissionDefinition permissionDefinition, String roleValue, boolean allowed) {
        this.permissionDefinition = permissionDefinition;
        this.roleValue = roleValue;
        this.allowed = allowed;
    }

    public void changeAllowed(boolean allowed) {
        this.allowed = allowed;
    }
}
