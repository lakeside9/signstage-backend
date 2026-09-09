-- 데모 행사 관리를 플랫폼 관리자 전담으로 전환 — signstage-docs
-- business/demo-account-exhibition-signer-preview-review.md 11장(2026-09-09, 결정 번복).
--
-- Organization.isDemo: 데모 조직 여부. 데모 조직에서는 CeremonyService.findActiveMemberOrThrow가
-- 실제 organization_members 행 없이도 플랫폼 관리자를 가상 멤버(OWNER 또는 VIEWER)로 우회시켜준다
-- (11.2절). 기본값 FALSE — 기존 조직은 전부 데모가 아니라는 뜻이라 backfill이 필요 없다.
ALTER TABLE organizations
    ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE AFTER billing_currency_code;

ALTER TABLE organization_histories
    ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE AFTER billing_currency_code;

-- ACTION_DEMO_CEREMONY_MANAGE — PLATFORM_OPS 이상만 데모 조직에서 전체 관리(OWNER 가상 멤버)를
-- 할 수 있다. PLATFORM_SUPPORT는 findActiveMemberOrThrow의 같은 우회를 타되 VIEWER로만 취급돼
-- 조회만 가능하다(11.5절) — 새 권한 축을 만들지 않고 기존 동적 RBAC(ACTION_BILLING_CATALOG_MANAGE
-- 등과 같은 패턴)에 얹는다.
INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order)
SELECT 'PLATFORM', 'MENU_DEMO_CEREMONIES', 'navigation.demoCeremonies', '/admin/demo-ceremonies', 'PlayCircle',
       COALESCE((SELECT MAX(display_order) FROM menus m WHERE m.console = 'PLATFORM'), -1) + 1;

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'PLATFORM', id, label_key, display_order
FROM menus WHERE menu_key = 'MENU_DEMO_CEREMONIES';

-- 메뉴 진입(화면 자체를 보는 것)은 PLATFORM_SUPPORT 이상 누구나 — 다른 메뉴 항목들과 같은 관례
-- (조회는 등급 구분 없이 열어두고, 실제 관리 액션만 세분화한다).
INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'MENU_DEMO_CEREMONIES' AND pd.permission_type = 'MENU';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_DEMO_CEREMONY_MANAGE', 'ACTION', 'PLATFORM', id, 'permission.action.demoCeremonyManage', 0
FROM menus WHERE menu_key = 'MENU_DEMO_CEREMONIES';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_DEMO_CEREMONY_MANAGE';
