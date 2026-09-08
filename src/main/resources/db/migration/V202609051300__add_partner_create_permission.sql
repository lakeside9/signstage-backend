-- 하드코딩 교체 착수 중 발견 — PlatformAdminOrganizationService#createOrganization(조직 생성)은
-- updateOrganizationStatus/updateOrganizationInfo(ACTION_PARTNER_STATUS_CHANGE/
-- ACTION_PARTNER_INFO_EDIT, V202609051200에 이미 시딩)와 다른 별개 액션인데 권한키가
-- 없었다 — 세 메서드 모두 지금은 같은 ORGANIZATION_CONTROL_ALLOWED_ROLES(PLATFORM_OPS,
-- PLATFORM_SUPER)를 쓰지만 의미가 다르므로 관리 화면 매트릭스에서 따로 켜고 끌 수 있게
-- 별도 권한키로 시딩한다 — signstage-docs
-- business/menu-and-action-permission-management-review.md 9장.

INSERT INTO permission_definitions (permission_key, permission_type, role_axis, menu_id, label_key, display_order)
SELECT 'ACTION_PARTNER_CREATE', 'ACTION', 'PLATFORM', id, 'permission.action.partnerCreate', 2
FROM menus WHERE menu_key = 'MENU_PARTNERS';

INSERT INTO role_permissions (permission_definition_id, role_value, allowed)
SELECT pd.id, roles.role_value, (roles.role_value IN ('PLATFORM_OPS', 'PLATFORM_SUPER'))
FROM permission_definitions pd
CROSS JOIN (
    SELECT 'PLATFORM_SUPPORT' AS role_value
    UNION ALL SELECT 'PLATFORM_OPS'
    UNION ALL SELECT 'PLATFORM_SUPER'
) roles
WHERE pd.permission_key = 'ACTION_PARTNER_CREATE';
