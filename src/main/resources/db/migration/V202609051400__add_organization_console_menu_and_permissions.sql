-- 조직 사용자 콘솔(ORGANIZATION 축, MemberRole 4단계) 메뉴/권한 시딩 — signstage-docs
-- business/menu-and-action-permission-management-review.md 5.2/9장. 스키마는
-- V202609051200에서 이미 두 축을 함께 지원하도록 만들어 뒀다 — 이번엔 PLATFORM 콘솔과
-- 같은 방식으로 ORGANIZATION 콘솔 행만 채운다(착수 범위, 12장 결정 #2 "플랫폼 먼저" 다음 단계).
--
-- 지금 UserLayout.tsx는 메뉴 4개(대시보드/행사 관리/설정 하위 회사정보관리·내 정보)를
-- 역할 구분 없이 전부 보여준다 — role_permissions도 전부 allowed=TRUE로 시딩해 배포 직후
-- 동작이 바뀌지 않게 한다(7.5절). menu_key는 PLATFORM 콘솔과 겹치지 않게 MENU_ORG_ 접두사를
-- 쓴다(menus.menu_key는 콘솔과 무관하게 전체 UNIQUE라서).

INSERT INTO menus (console, menu_key, label_key, path, icon_key, display_order) VALUES
    ('ORGANIZATION', 'MENU_ORG_DASHBOARD',  'navigation.dashboard',    '/',             'LayoutDashboard', 0),
    ('ORGANIZATION', 'MENU_ORG_CEREMONIES', 'navigation.ceremonies',   '/ceremonies',   'FileSignature',   1),
    ('ORGANIZATION', 'MENU_ORG_SETTINGS',   'common.settings',         NULL,            'Settings',        2);

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'ORGANIZATION', id, 'MENU_ORG_SETTINGS_COMPANY_INFO', 'navigation.organization', '/organizations', 'Building2', 0
FROM menus WHERE menu_key = 'MENU_ORG_SETTINGS';

INSERT INTO menus (console, parent_menu_id, menu_key, label_key, path, icon_key, display_order)
SELECT 'ORGANIZATION', id, 'MENU_ORG_PROFILE', 'navigation.profile', '/profile', 'User', 1
FROM menus WHERE menu_key = 'MENU_ORG_SETTINGS';

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT menu_key, 'MENU', 'ORGANIZATION', id, label_key, display_order FROM menus WHERE console = 'ORGANIZATION';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, TRUE
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value
    UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR'
    UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.role_axis = 'ORGANIZATION' AND pd.permission_type = 'MENU';

-- ── ACTION 권한키(발췌, 9장) ──
-- CeremonyService#checkCanCreateCeremony/#checkCeremonyManageAccess의 VIEWER 거부,
-- MemberService#checkCanManageMembers(초대/역할변경/제거 공용), OrganizationService#
-- updateOrganization(OWNER 전용)을 옮긴다. OPERATOR의 "본인/배정 건만", 최소 1 OWNER,
-- "OWNER 지정은 OWNER만" 같은 소유권 기반 불변식은 이 메커니즘으로 옮기지 않고 코드에
-- 그대로 남긴다(3/4장) — role_permissions는 "이 역할이 이 액션 자체에 접근할 자격이
-- 있는가"까지만 다룬다.

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_CEREMONY_CREATE', 'ACTION', 'ORGANIZATION', id, 'permission.action.ceremonyCreate', 0 FROM menus WHERE menu_key = 'MENU_ORG_CEREMONIES'
UNION ALL
SELECT 'ACTION_CEREMONY_MANAGE', 'ACTION', 'ORGANIZATION', id, 'permission.action.ceremonyManage', 1 FROM menus WHERE menu_key = 'MENU_ORG_CEREMONIES'
UNION ALL
SELECT 'ACTION_MEMBER_MANAGE', 'ACTION', 'ORGANIZATION', id, 'permission.action.memberManage', 0 FROM menus WHERE menu_key = 'MENU_ORG_SETTINGS_COMPANY_INFO'
UNION ALL
SELECT 'ACTION_COMPANY_INFO_EDIT', 'ACTION', 'ORGANIZATION', id, 'permission.action.companyInfoEdit', 1 FROM menus WHERE menu_key = 'MENU_ORG_SETTINGS_COMPANY_INFO';

-- OWNER/ADMIN/OPERATOR 허용, VIEWER 거부(현재 checkCanCreateCeremony/checkCeremonyManageAccess와 동일)
INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value <> 'VIEWER')
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value
    UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR'
    UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key IN ('ACTION_CEREMONY_CREATE', 'ACTION_CEREMONY_MANAGE');

-- OWNER/ADMIN만 허용(현재 checkCanManageMembers와 동일)
INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('OWNER', 'ADMIN'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value
    UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR'
    UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key = 'ACTION_MEMBER_MANAGE';

-- OWNER만 허용(현재 OrganizationService#updateOrganization과 동일)
INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value = 'OWNER')
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'OWNER' AS role_value
    UNION ALL SELECT 'ADMIN'
    UNION ALL SELECT 'OPERATOR'
    UNION ALL SELECT 'VIEWER'
) roles
WHERE pd.permission_key = 'ACTION_COMPANY_INFO_EDIT';
