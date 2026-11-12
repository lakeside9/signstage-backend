-- 파트너 평가·영업 마진 시뮬레이션 — signstage-docs business/platform-order-partner-subcontract-review.md
-- 11장 8단계(2026-11-12).

-- 검수 때 플랫폼이 매기는 수행 평점(1~5, 선택). 승인 간주 건은 비운다.
ALTER TABLE order_inspections
    ADD COLUMN performance_rating INT NULL AFTER deduction_reason,
    ADD CONSTRAINT ck_order_inspection_rating CHECK (performance_rating IS NULL OR performance_rating BETWEEN 1 AND 5);

-- 메뉴: 관리자 "파트너 평가"·"마진 시뮬레이션"(조회 전용 — 메뉴 권한만 둔다)
INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, m.menu_key, m.label_key, m.path, m.icon_key, m.display_order
FROM menus g
CROSS JOIN (
    SELECT 'MENU_PARTNER_EVALUATIONS' AS menu_key, 'navigation.partnerEvaluations' AS label_key,
           '/admin/partner-evaluations' AS path, 'Star' AS icon_key, 7 AS display_order
    UNION ALL SELECT 'MENU_MARGIN_SIMULATOR', 'navigation.marginSimulator', '/admin/margin-simulator', 'Calculator', 8
) m
WHERE g.menu_key = 'MENU_GROUP_ORDER';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', console, id, label_key, display_order
FROM menus
WHERE menu_key IN ('MENU_PARTNER_EVALUATIONS', 'MENU_MARGIN_SIMULATOR');

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN ('MENU_PARTNER_EVALUATIONS', 'MENU_MARGIN_SIMULATOR') AND pd.permission_type = 'MENU';
