-- 행사 이벤트 효과 관리 메뉴/권한 시딩 — signstage-docs
-- business/ceremony-event-effect-implementation-tasks.md PRE-04, BE-CATALOG-03 참고.
--
-- 메뉴는 V202609051200이 시딩한 PLATFORM 콘솔 메뉴 목록의 표시 순서 6에 끼워 넣는다
-- (BILLING_SIMULATOR/PURCHASE_REQUESTS/AUDIT_LOGS/PROFILE을 한 칸씩 뒤로 민다). 메뉴 조회
-- 권한(MENU_EFFECTS)은 다른 플랫폼 메뉴처럼 세 등급 모두 허용하고, 실제 등록·수정·순서
-- 이동 액션(ACTION_EFFECT_MANAGE)은 과금 카탈로그 관리와 같은 PLATFORM_OPS 이상만 허용한다
-- (PLATFORM_SUPPORT는 조회·미리보기만 가능 — 미리보기 자체는 별도 권한키 없이 메뉴 진입만
-- 요구하는 FE-ADMIN-03 범위다).

UPDATE menus SET display_order = display_order + 1
WHERE console = 'PLATFORM' AND display_order >= 6;

INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order) VALUES
    ('PLATFORM', 'MENU_EFFECTS', 'navigation.effects', '/admin/effects', 'Sparkles', 6);

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'MENU_EFFECTS', 'MENU', 'PLATFORM', id, 'navigation.effects', 6 FROM menus WHERE menu_key = 'MENU_EFFECTS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'MENU_EFFECTS' AND pd.permission_type = 'MENU';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_EFFECT_MANAGE', 'ACTION', 'PLATFORM', id, 'permission.action.effectManage', 0
FROM menus WHERE menu_key = 'MENU_EFFECTS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_EFFECT_MANAGE';
