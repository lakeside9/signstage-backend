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
 * {@link Menu} 구조(경로/아이콘/순서/사용여부) 변경 이력. append-only다 — 카탈로그
 * {@code *_histories}(예: {@code OptionalFeatureHistory})와 같은 패턴. 관리 화면에서 메뉴
 * 이름/경로/순서까지 편집을 허용하기로 한 결정(signstage-docs
 * business/menu-and-action-permission-management-review.md 12장 #10, 2026-09-05)에 따라
 * 다른 관리형 카탈로그와 동일하게 변경 이력을 남긴다.
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
        this.path = menu.getPath();
        this.iconKey = menu.getIconKey();
        this.displayOrder = menu.getDisplayOrder();
        this.active = menu.isActive();
    }
}
