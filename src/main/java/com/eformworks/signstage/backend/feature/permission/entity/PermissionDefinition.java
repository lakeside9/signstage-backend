package com.eformworks.signstage.backend.feature.permission.entity;

import com.eformworks.signstage.backend.core.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * 이 시스템이 다루는 권한키 마스터(카탈로그) — signstage-docs
 * business/menu-and-action-permission-management-review.md 7.2절. {@link RolePermission}이
 * "이미 설정된 조합"만 알 수 있는 것과 달리, 이 테이블은 "아직 아무도 안 건드린 새 메뉴/버튼"도
 * 포함한 권한키 전체 목록을 관리 화면에 제공한다.
 *
 * <p>이 행은 코드가 배포될 때만 늘어난다({@code OptionalFeatureCode} enum이 배포로만
 * 늘어나는 것과 같은 이유, 12장 결정 #9) — 관리자가 화면에서 새 권한키를 자유롭게 만들 수는
 * 없다. {@code MENU} 타입은 {@link Menu} 행 하나와 1:1로 짝짓고, {@code ACTION} 타입은
 * 메뉴 트리에 속하지 않는 화면 안 개별 액션이라 {@code menu}가 그루핑 목적의 선택 값이다.
 */
@Entity
@Table(name = "permission_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermissionDefinition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permission_key", nullable = false, unique = true, length = 100)
    private String permissionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type", nullable = false, length = 20)
    private PermissionType permissionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_axis", nullable = false, length = 20)
    private RoleAxis roleAxis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    @Column(name = "label_key", nullable = false, length = 150)
    private String labelKey;

    @Column(name = "description_key", length = 150)
    private String descriptionKey;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Builder
    private PermissionDefinition(
            String permissionKey, PermissionType permissionType, RoleAxis roleAxis,
            Menu menu, String labelKey, String descriptionKey, int displayOrder
    ) {
        this.permissionKey = permissionKey;
        this.permissionType = permissionType;
        this.roleAxis = roleAxis;
        this.menu = menu;
        this.labelKey = labelKey;
        this.descriptionKey = descriptionKey;
        this.displayOrder = displayOrder;
        this.active = true;
    }
}
