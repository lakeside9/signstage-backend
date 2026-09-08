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
 * {@link Menu} 구조(상위 메뉴/경로/아이콘/순서/사용여부) 변경 이력. append-only다 — 카탈로그
 * {@code *_histories}(예: {@code OptionalFeatureHistory})와 같은 패턴. 관리 화면에서 메뉴
 * 이름/경로/순서까지 편집을 허용하기로 한 결정(signstage-docs
 * business/menu-and-action-permission-management-review.md 12장 #10, 2026-09-05)에 따라
 * 다른 관리형 카탈로그와 동일하게 변경 이력을 남긴다. 레벨 이동(상위↔하위) 기능 추가(2026-09-05
 * 후속)에 맞춰 {@code parentMenuId}도 스냅샷에 포함한다.
 */
@Entity
@Table(name = "menu_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class MenuHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    /** 스냅샷 시점의 상위 메뉴 id — 최상위면 NULL. FK를 걸지 않는다(상위 메뉴가 나중에 바뀌어도 과거 스냅샷은 그대로 남아야 한다). */
    @Column(name = "parent_menu_id")
    private Long parentMenuId;

    @Column(length = 200)
    private String path;

    @Column(name = "icon_key", length = 50)
    private String iconKey;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Builder
    private MenuHistory(Menu menu) {
        this.menu = menu;
        this.parentMenuId = menu.getParentMenu() == null ? null : menu.getParentMenu().getId();
        this.path = menu.getPath();
        this.iconKey = menu.getIconKey();
        this.displayOrder = menu.getDisplayOrder();
        this.active = menu.isActive();
    }
}
