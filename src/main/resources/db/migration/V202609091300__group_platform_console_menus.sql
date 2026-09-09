-- 플랫폼 관리자 콘솔 메뉴를 업무별로 그룹화 — signstage-docs
-- business/menu-and-action-permission-management-review.md 7.1절에서 이미 예정해둔
-- "설정처럼 여닫는 그룹" 패턴(Menu.parentMenu, path=NULL)을 처음으로 실제 사용한다.
-- 스키마/프런트(SidebarMenuTree)는 이미 재귀 트리를 그리므로 이 마이그레이션은 순수 데이터
-- 변경이다 — 그룹 헤더 5행을 추가하고, 기존 14개 메뉴의 parent_menu_id/display_order만 옮긴다.
--
-- 그룹 헤더도 기존 leaf 메뉴와 동일하게 MENU 타입 permission_definitions + role_permissions를
-- 만든다(3개 역할 모두 TRUE) — MenuService.getMenuTree는 짝이 되는 권한키가 없으면 기본
-- 노출로 처리하지만, 이 프로젝트의 기존 관례(메뉴마다 명시적 MENU 권한행을 둔다)를 그대로 따른다.
-- 하위 메뉴 14개는 전부 3개 역할 모두에게 노출돼 있어(2026-09-09 확인) 헤더를 숨기는 역할이
-- 없다 — 고아 메뉴(부모가 안 보여 트리에서 잘리는 자식) 위험이 없다.

INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order) VALUES
    ('PLATFORM', 'MENU_GROUP_MEMBERS', 'navigation.groupMembers', NULL, 'UsersRound', 1),
    ('PLATFORM', 'MENU_GROUP_BILLING', 'navigation.groupBilling', NULL, 'Wallet', 6),
    ('PLATFORM', 'MENU_GROUP_PURCHASE', 'navigation.groupPurchase', NULL, 'BadgePercent', 10),
    ('PLATFORM', 'MENU_GROUP_DEMO', 'navigation.groupDemo', NULL, 'FlaskConical', 14),
    ('PLATFORM', 'MENU_GROUP_SYSTEM', 'navigation.groupSystem', NULL, 'Settings', 16);

-- 기존 14개 메뉴를 그룹 밑으로 옮기고, 최상위 2개(대시보드/내 프로필)를 포함해 전체 순서를
-- 0~18로 다시 매긴다.
UPDATE menus SET display_order = 0 WHERE menu_key = 'MENU_DASHBOARD';

UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_MEMBERS') g
    SET m.parent_menu_id = g.id, m.display_order = 2 WHERE m.menu_key = 'MENU_PARTNERS';
UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_MEMBERS') g
    SET m.parent_menu_id = g.id, m.display_order = 3 WHERE m.menu_key = 'MENU_PARTNER_REQUESTS';
UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_MEMBERS') g
    SET m.parent_menu_id = g.id, m.display_order = 4 WHERE m.menu_key = 'MENU_USERS';
UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_MEMBERS') g
    SET m.parent_menu_id = g.id, m.display_order = 5 WHERE m.menu_key = 'MENU_ACCOUNTS';

UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_BILLING') g
    SET m.parent_menu_id = g.id, m.display_order = 7 WHERE m.menu_key = 'MENU_BILLING_CATALOG';
UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_BILLING') g
    SET m.parent_menu_id = g.id, m.display_order = 8 WHERE m.menu_key = 'MENU_EFFECTS';
UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_BILLING') g
    SET m.parent_menu_id = g.id, m.display_order = 9 WHERE m.menu_key = 'MENU_BILLING_SIMULATOR';

UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_PURCHASE') g
    SET m.parent_menu_id = g.id, m.display_order = 11 WHERE m.menu_key = 'MENU_PURCHASE_REQUESTS';
UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_PURCHASE') g
    SET m.parent_menu_id = g.id, m.display_order = 12 WHERE m.menu_key = 'MENU_CEREMONY_DISCOUNTS';
UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_PURCHASE') g
    SET m.parent_menu_id = g.id, m.display_order = 13 WHERE m.menu_key = 'MENU_ORGANIZATION_DISCOUNT_OVERRIDES';

UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_DEMO') g
    SET m.parent_menu_id = g.id, m.display_order = 15 WHERE m.menu_key = 'MENU_DEMO_CEREMONIES';

UPDATE menus m, (SELECT id FROM menus WHERE menu_key = 'MENU_GROUP_SYSTEM') g
    SET m.parent_menu_id = g.id, m.display_order = 17 WHERE m.menu_key = 'MENU_AUDIT_LOGS';

UPDATE menus SET display_order = 18 WHERE menu_key = 'MENU_PROFILE';

-- 그룹 헤더 5개에 대한 MENU 권한행 — 기존 리프 메뉴와 같은 관례로 3개 역할 모두 노출.
INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order
FROM menus WHERE menu_key IN
    ('MENU_GROUP_MEMBERS', 'MENU_GROUP_BILLING', 'MENU_GROUP_PURCHASE', 'MENU_GROUP_DEMO', 'MENU_GROUP_SYSTEM');

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_type = 'MENU' AND pd.permission_key IN
    ('MENU_GROUP_MEMBERS', 'MENU_GROUP_BILLING', 'MENU_GROUP_PURCHASE', 'MENU_GROUP_DEMO', 'MENU_GROUP_SYSTEM');
