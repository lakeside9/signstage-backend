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
 * 메뉴 트리 구조 자체(계층·경로·아이콘·순서)를 담는 카탈로그. {@code AdminLayout.tsx}/
 * {@code UserLayout.tsx}의 하드코딩된 {@code NAV_ITEMS} 배열이 하던 일을 옮긴다 — signstage-docs
 * business/menu-and-action-permission-management-review.md 7.1절. "이 메뉴를 볼 수 있는 역할이
 * 무엇인가"는 이 엔티티가 아니라 짝이 되는 {@link PermissionDefinition}(타입 MENU) +
 * {@link RolePermission}이 담당한다 — 메뉴 구조와 노출 여부는 관심사가 분리돼 있다.
 *
 * <p>구조 변경(부모/경로/아이콘/순서/사용여부)은 {@link MenuHistory}에, 언어별 이름은
 * {@link MenuTranslation}(변경 이력은 {@link MenuTranslationHistory})에 별도로 남긴다 —
 * 메뉴명을 자유 텍스트로 이 테이블에 직접 두지 않는 이유는 다국어 처리 설계(signstage-docs
 * business/multilingual-content-and-error-handling-review.md 6장)를 그대로 따르기 위함이다.
 */
@Entity
@Table(name = "menus")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleAxis console;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_menu_id")
    private Menu parentMenu;

    @Column(name = "menu_key", nullable = false, unique = true, length = 100)
    private String menuKey;

    /** 배포 관리형 기본 표시명 번역 키(예: navigation.ceremonies). 언어별 오버라이드가 없을 때 쓰는 fallback이다. */
    @Column(name = "label_key", nullable = false, length = 150)
    private String labelKey;

    /** 하위 메뉴를 여닫기만 하는 그룹(예: "설정")은 경로가 없어 NULL이다. */
    @Column(length = 200)
    private String path;

    /** lucide-react 아이콘 이름 — 프런트가 문자열→컴포넌트로 매핑한다. */
    @Column(name = "icon_key", length = 50)
    private String iconKey;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Builder
    private Menu(RoleAxis console, Menu parentMenu, String menuKey, String labelKey, String path, String iconKey, int displayOrder) {
        this.console = console;
        this.parentMenu = parentMenu;
        this.menuKey = menuKey;
        this.labelKey = labelKey;
        this.path = path;
        this.iconKey = iconKey;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    /**
     * 구조 변경 — 이름(번역)은 다루지 않는다(관심사 분리, {@link MenuTranslation} 참고).
     * 호출자가 변경 전후로 {@link MenuHistory} 스냅샷을 남긴다.
     */
    public void updateStructure(String path, String iconKey, int displayOrder, boolean active) {
        this.path = path;
        this.iconKey = iconKey;
        this.displayOrder = displayOrder;
        this.active = active;
    }
}
