-- 과금 카탈로그 메뉴 재편 — "선택옵션"/"용량 추가구매" 2개 메뉴를 "단위 상품" 1개로 합친다
-- (signstage-docs business/billing-catalog-unit-product-model-redesign-review.md 결정,
-- 2026-09-10, 3단계 프런트엔드 전환). 프런트 화면도 8개(선택옵션/용량추가구매 각 4)에서
-- 4개(단위 상품 목록/등록/상세/수정)로 통합됐다.
--
-- 기존 관례(V202609091500 등) 그대로 — 메뉴 행은 지우지 않고 비활성화만 한다("메뉴 자체를
-- 새로 만들거나 삭제할 수 없다, 배포로만 등록/변경한다", AdminMenuManager.tsx 안내문). 이력은
-- menu_histories에 자연스럽게 남는다.
UPDATE menus SET active = FALSE WHERE menu_key IN ('MENU_OPTIONAL_FEATURES', 'MENU_CAPACITY_ADDONS');
UPDATE permission_definitions SET active = FALSE
    WHERE permission_key IN ('MENU_OPTIONAL_FEATURES', 'MENU_CAPACITY_ADDONS') AND permission_type = 'MENU';

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_UNIT_PRODUCTS', 'navigation.unitProducts', '/admin/billing-catalog/unit-products', 'Boxes', 8
FROM menus g WHERE g.menu_key = 'MENU_GROUP_BILLING';

-- 형제 메뉴(이벤트 효과/과금 시뮬레이터) 순서를 메뉴 1개가 줄어든 만큼 앞으로 당긴다.
UPDATE menus SET display_order = 9 WHERE menu_key = 'MENU_EFFECTS';
UPDATE menus SET display_order = 10 WHERE menu_key = 'MENU_BILLING_SIMULATOR';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order
FROM menus WHERE menu_key = 'MENU_UNIT_PRODUCTS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_type = 'MENU' AND pd.permission_key = 'MENU_UNIT_PRODUCTS';
