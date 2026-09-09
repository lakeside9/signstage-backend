-- "권한관리"/"메뉴관리" 화면 자체로 가는 링크를 menus 테이블에 정식 등록하고 "시스템" 그룹
-- (MENU_GROUP_SYSTEM, V202609091300) 아래로 옮긴다 — 사용자 요청(2026-09-09): "menus 에
-- 등록하고, 시스템 메뉴 아래로 옮겨주세요."
--
-- 지금까지 이 둘은 자기 잠금(lockout) 방지를 이유로 menus 테이블에 아예 없이 AdminLayout.tsx가
-- PLATFORM_SUPER에게만 하드코딩으로 붙여왔다(2026-09-05, 12장 결정 #6) — 그래서 "메뉴관리"
-- 화면(menus 테이블을 그대로 조회)에도 안 보였다. 이 마이그레이션 이후로는 다른 메뉴와 똑같이
-- role_permissions로 노출을 결정한다: PLATFORM_SUPER만 TRUE로 시딩해 기존과 동일한 접근 범위를
-- 유지한다(백엔드 컨트롤러 쪽도 별개로 PLATFORM_SUPER 하드코딩 검사를 그대로 유지하므로, 이
-- role_permissions 값이 실수로 꺼지더라도 API 자체가 뚫리지는 않는다 — 사이드바에서만 안 보이게
-- 될 뿐이고, 직접 URL로는 여전히 들어갈 수 있다).
INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_PERMISSION_MANAGEMENT', 'navigation.permissionManagement', '/admin/permissions', 'KeyRound', 18
FROM menus g WHERE g.menu_key = 'MENU_GROUP_SYSTEM';

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', g.id, 'MENU_MENU_MANAGEMENT', 'navigation.menuManagement', '/admin/menus', 'Layers', 19
FROM menus g WHERE g.menu_key = 'MENU_GROUP_SYSTEM';

UPDATE menus SET display_order = 20 WHERE menu_key = 'MENU_PROFILE';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order
FROM menus WHERE menu_key IN ('MENU_PERMISSION_MANAGEMENT', 'MENU_MENU_MANAGEMENT');

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value = 'PLATFORM_SUPER')
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_type = 'MENU' AND pd.permission_key IN ('MENU_PERMISSION_MANAGEMENT', 'MENU_MENU_MANAGEMENT');
