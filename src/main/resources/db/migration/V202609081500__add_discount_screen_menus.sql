-- 조직 상세에 묻혀 있던 '행사 건별 재량 할인'·'파트너별 할인 오버라이드' 화면을 별도 목록/상세
-- 화면으로 분리 — signstage-docs business/discount-management-screen-separation-review.md 결정
-- #3(완전 독립 최상위 메뉴 2개)/#4(동적 RBAC로 시딩). 변경 액션은 새로 만들지 않고
-- V202609081400이 이미 시딩한 ACTION_CEREMONY_FINAL_DISCOUNT_MANAGE/
-- ACTION_ORGANIZATION_DISCOUNT_MANAGE를 그대로 재사용한다.
--
-- 현재 PLATFORM 콘솔 순서(0 DASHBOARD ~ 10 PROFILE, V202609072200까지 반영)에서
-- PURCHASE_REQUESTS(8) 뒤, AUDIT_LOGS(9) 앞에 끼워 넣는다.

UPDATE menus SET display_order = display_order + 2
WHERE console = 'PLATFORM' AND display_order >= 9;

INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order) VALUES
    ('PLATFORM', 'MENU_CEREMONY_DISCOUNTS', 'navigation.ceremonyDiscounts', '/admin/ceremony-discounts', 'Percent', 9),
    ('PLATFORM', 'MENU_ORGANIZATION_DISCOUNT_OVERRIDES', 'navigation.organizationDiscountOverrides', '/admin/organization-discount-overrides', 'Tag', 10);

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order
FROM menus WHERE menu_key IN ('MENU_CEREMONY_DISCOUNTS', 'MENU_ORGANIZATION_DISCOUNT_OVERRIDES');

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key IN ('MENU_CEREMONY_DISCOUNTS', 'MENU_ORGANIZATION_DISCOUNT_OVERRIDES') AND pd.permission_type = 'MENU';
